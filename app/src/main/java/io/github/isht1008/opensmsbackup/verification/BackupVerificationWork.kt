package io.github.isht1008.opensmsbackup.verification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupConversationLimiter
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupScope
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshotBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

object BackupVerificationWorkContract {
    const val TAG = "backup-verification"
    private const val PROFILE = "verification.profile"
    private const val DEVICE = "verification.device"
    private const val STAGE = "verification.stage"
    private const val PROCESSED = "verification.processed"
    private const val TOTAL = "verification.total"
    private const val CREATED_PREFIX = "backup-verification-created-"
    private const val PROFILE_TAG_PREFIX = "backup-verification-profile-"
    fun uniqueName(profileId: String, deviceId: String) = "backup-verification-$profileId-$deviceId"
    fun input(profileId: String, deviceId: String) = Data.Builder().putString(PROFILE, profileId).putString(DEVICE, deviceId).build()
    fun profile(data: Data) = data.getString(PROFILE)
    fun device(data: Data) = data.getString(DEVICE)
    fun progress(value: BackupVerificationProgress) = Data.Builder().putString(STAGE, value.stage.name)
        .putInt(PROCESSED, value.processed).putInt(TOTAL, value.total ?: -1).build()
    fun readProgress(data: Data): BackupVerificationProgress? = data.getString(STAGE)?.let {
        BackupVerificationProgress(VerificationStage.valueOf(it), data.getInt(PROCESSED, 0),
            data.getInt(TOTAL, -1).takeIf { total -> total >= 0 })
    }
    fun createdTag(value: Long) = "$CREATED_PREFIX$value"
    fun profileTag(profileId: String) = "$PROFILE_TAG_PREFIX$profileId"
    fun createdAt(tags: Set<String>) = tags.firstNotNullOfOrNull {
        it.removePrefix(CREATED_PREFIX).takeIf { _ -> it.startsWith(CREATED_PREFIX) }?.toLongOrNull()
    } ?: 0L
}

class BackupVerificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val profileId = BackupVerificationWorkContract.profile(inputData) ?: return Result.failure()
        val expectedDevice = BackupVerificationWorkContract.device(inputData) ?: return Result.failure()
        val profile = GmailAccountManager(applicationContext).getAccountProfile(profileId) ?: return Result.failure()
        if (profile.connectionState != AccountProfileEntity.CONNECTION_STATE_CONNECTED) return Result.failure()
        val deviceStore = DeviceProfileStore.create(applicationContext)
        val device = deviceStore.getOrCreate()
        if (device.deviceId != expectedDevice) return Result.failure()
        val mode = MultiAccountRepository.create(applicationContext).getBackupMode(profileId)
        val allLocal = SmsRepository().getSmsMessages(applicationContext, false)
        val localScope = GmailBackupConversationLimiter.applyConversations(
            SmsConversationSnapshotBuilder().build(allLocal),
            GmailBackupScope.RECENT_TEST
        )
        val local = localScope.conversations.flatMap { it.messages }
        val request = BackupVerificationRequest(profileId, profile.accountEmail, device.deviceId,
            device.displayName, mode, device.defaultRegion, local,
            completeLocalScope = !localScope.isLimitedTest)
        val labelId = deviceStore.getGmailDeviceLabelId(profileId)
        val repository = if (labelId == null) {
            VerificationArchiveRepository { kotlin.Result.failure(IllegalStateException("Device archive label unavailable")) }
        } else {
            val gateway = AndroidVerificationGmailGateway(GmailApiClient(applicationContext).createService(profile), profileId)
            GmailVerificationRepository(gateway, labelId)
        }
        val result = DefaultBackupVerificationEngine(repository).verify(request) {
            setProgress(BackupVerificationWorkContract.progress(it))
            if (notificationsAllowed()) setForeground(foreground(it))
        }
        val id = withContext(NonCancellable) {
            DatabaseProvider.getDatabase(applicationContext).backupVerificationDao().insert(result.toEntity())
        }
        return Result.success(Data.Builder().putLong("verification.result_id", id).build())
    }

    private fun notificationsAllowed() = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun foreground(progress: BackupVerificationProgress): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "OpenSMSBackup – Backup Verification", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Verifying Gmail backup")
            .setContentText(progress.stage.name.replace('_', ' ').lowercase())
            .setProgress(progress.total ?: 0, progress.processed, progress.total == null).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel verification",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(32_000 + (id.hashCode() and 0xFFF), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    private companion object { const val CHANNEL = "backup_verification_progress" }
}

class BackupVerificationWorkCoordinator(private val context: Context) {
    suspend fun enqueue(profileId: String, deviceId: String): UUID {
        val manager = WorkManager.getInstance(context)
        val unique = BackupVerificationWorkContract.uniqueName(profileId, deviceId)
        manager.getWorkInfosForUniqueWorkFlow(unique).first().firstOrNull { !it.state.isFinished }?.let { return it.id }
        val request = OneTimeWorkRequestBuilder<BackupVerificationWorker>()
            .setInputData(BackupVerificationWorkContract.input(profileId, deviceId))
            .addTag(BackupVerificationWorkContract.TAG)
            .addTag(BackupVerificationWorkContract.profileTag(profileId))
            .addTag(BackupVerificationWorkContract.createdTag(System.currentTimeMillis())).build()
        manager.enqueueUniqueWork(unique, ExistingWorkPolicy.KEEP, request)
        return request.id
    }
    fun observe() = WorkManager.getInstance(context).getWorkInfosByTagFlow(BackupVerificationWorkContract.TAG)
    suspend fun hasActiveWork(profileId: String): Boolean = WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(BackupVerificationWorkContract.profileTag(profileId))
        .first().any { !it.state.isFinished }
    fun cancel(id: UUID) = WorkManager.getInstance(context).cancelWorkById(id)
}
