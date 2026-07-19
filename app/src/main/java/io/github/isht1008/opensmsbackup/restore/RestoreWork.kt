package io.github.isht1008.opensmsbackup.restore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import java.util.UUID
import kotlinx.coroutines.flow.first

object RestoreWorkContract {
    const val TAG = "sms-restore"
    private const val CREATED_PREFIX = "sms-restore-created-"
    private const val PLAN_ID = "restore.plan_id"
    private const val SELECTED = "restore.selected"
    private const val RESTORED = "restore.restored"
    private const val SKIPPED = "restore.skipped"
    private const val FAILED = "restore.failed"
    private const val CANCELLED = "restore.cancelled"
    fun input(planId: String) = Data.Builder().putString(PLAN_ID, planId).build()
    fun planId(data: Data) = data.getString(PLAN_ID)
    fun data(result: RestoreResult) = Data.Builder().putInt(SELECTED, result.selected)
        .putInt(RESTORED, result.restored).putInt(SKIPPED, result.skippedDuplicates)
        .putInt(FAILED, result.failed).putBoolean(CANCELLED, result.cancelled).build()
    fun result(data: Data) = RestoreResult(data.getInt(SELECTED, 0), data.getInt(RESTORED, 0),
        data.getInt(SKIPPED, 0), data.getInt(FAILED, 0), data.getBoolean(CANCELLED, false))
    fun createdTag(value: Long) = "$CREATED_PREFIX$value"
    fun createdAt(tags: Set<String>) = tags.firstNotNullOfOrNull {
        it.removePrefix(CREATED_PREFIX).takeIf { _ -> it.startsWith(CREATED_PREFIX) }?.toLongOrNull()
    } ?: 0L
}

class RestoreResultStore(context: Context) {
    private val preferences = context.getSharedPreferences("restore_results", Context.MODE_PRIVATE)
    fun write(workId: UUID, result: RestoreResult) {
        preferences.edit().putString(workId.toString(), listOf(
            result.selected, result.restored, result.skippedDuplicates, result.failed,
            if (result.cancelled) 1 else 0
        ).joinToString(",")).commit()
    }
    fun read(workId: UUID): RestoreResult? = preferences.getString(workId.toString(), null)
        ?.split(',')?.mapNotNull(String::toIntOrNull)?.takeIf { it.size == 5 }
        ?.let { RestoreResult(it[0], it[1], it[2], it[3], it[4] == 1) }
    fun writeSource(workId: UUID, accountEmail: String, deviceName: String) {
        preferences.edit().putString("${workId}.account", accountEmail)
            .putString("${workId}.device", deviceName).commit()
    }
    fun readSource(workId: UUID): Pair<String, String>? {
        val account = preferences.getString("${workId}.account", null) ?: return null
        val device = preferences.getString("${workId}.device", null) ?: return null
        return account to device
    }
}

class RestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val planId = RestoreWorkContract.planId(inputData) ?: return Result.failure()
        val store = RestorePlanStore(applicationContext)
        val plan = store.read(planId) ?: return Result.failure()
        if (!AndroidRestoreRoleGate(applicationContext).isGranted()) return Result.failure()
        val local = SmsRepository().getSmsMessages(applicationContext, false)
        val engine = DefaultRestoreEngine(AndroidSmsRestoreWriter(applicationContext))
        val result = engine.restore(plan.messages, local, plan.defaultRegion) { progress ->
            RestoreResultStore(applicationContext).write(id, progress)
            setProgress(RestoreWorkContract.data(progress))
            if (shouldNotify()) setForeground(foreground(progress))
        }
        RestoreResultStore(applicationContext).write(id, result)
        store.delete(planId)
        return Result.success(RestoreWorkContract.data(result))
    }

    private fun shouldNotify() = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun foreground(result: RestoreResult): ForegroundInfo {
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(
            "sms_restore_progress", "OpenSMSBackup – SMS Restore", android.app.NotificationManager.IMPORTANCE_LOW
        ))
        val done = result.restored + result.skippedDuplicates + result.failed
        val notification = NotificationCompat.Builder(applicationContext, "sms_restore_progress")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Restoring SMS")
            .setContentText("$done of ${result.selected} processed")
            .setProgress(result.selected, done, result.selected == 0).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel restore",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(28_000 + (id.hashCode() and 0xFFF), notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

class RestoreWorkCoordinator(private val context: Context) {
    suspend fun enqueue(plan: RestorePlan): UUID {
        val manager = WorkManager.getInstance(context)
        manager.getWorkInfosByTagFlow(RestoreWorkContract.TAG).first()
            .firstOrNull { !it.state.isFinished }?.let { return it.id }
        val planId = UUID.randomUUID().toString()
        RestorePlanStore(context).write(planId, plan)
        val request = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInputData(RestoreWorkContract.input(planId)).addTag(RestoreWorkContract.TAG)
            .addTag(RestoreWorkContract.createdTag(System.currentTimeMillis())).build()
        RestoreResultStore(context).writeSource(request.id, plan.accountEmail, plan.sourceDeviceName)
        manager.enqueueUniqueWork("sms-restore-active", ExistingWorkPolicy.KEEP, request)
        return request.id
    }
    fun observe() = WorkManager.getInstance(context).getWorkInfosByTagFlow(RestoreWorkContract.TAG)
    fun cancel(id: UUID) = WorkManager.getInstance(context).cancelWorkById(id)
}
