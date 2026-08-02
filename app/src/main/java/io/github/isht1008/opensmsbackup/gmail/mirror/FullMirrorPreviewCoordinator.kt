package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkContract
import io.github.isht1008.opensmsbackup.verification.BackupVerificationWorkContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

sealed interface FullMirrorPreviewEnqueueResult {
    data class Enqueued(val workId: UUID, val scanId: String) : FullMirrorPreviewEnqueueResult
    data class AlreadyRunning(val workId: UUID, val scanId: String) : FullMirrorPreviewEnqueueResult
    data class Blocked(val reason: String) : FullMirrorPreviewEnqueueResult
}

internal interface FullMirrorPreviewWorkGateway {
    suspend fun activeByTag(tag: String): WorkInfo?
    suspend fun activeUnique(name: String): WorkInfo?
    fun enqueueUnique(name: String, request: OneTimeWorkRequest)
    fun observeAll(): Flow<List<WorkInfo>>
    fun cancel(workId: UUID)
}

private class AndroidFullMirrorPreviewWorkGateway(
    private val workManager: WorkManager
) : FullMirrorPreviewWorkGateway {
    override suspend fun activeByTag(tag: String) =
        workManager.getWorkInfosByTagFlow(tag).first()
            .firstOrNull { FullMirrorPreviewWorkContract.isActive(it.state) }

    override suspend fun activeUnique(name: String) =
        workManager.getWorkInfosForUniqueWorkFlow(name).first()
            .firstOrNull { FullMirrorPreviewWorkContract.isActive(it.state) }

    override fun enqueueUnique(name: String, request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    override fun observeAll() =
        workManager.getWorkInfosByTagFlow(FullMirrorPreviewWorkContract.ALL_WORK_TAG)

    override fun cancel(workId: UUID) {
        workManager.cancelWorkById(workId)
    }
}

class FullMirrorPreviewCoordinator private constructor(
    private val context: Context,
    private val gateway: FullMirrorPreviewWorkGateway
) {
    constructor(context: Context) : this(
        context.applicationContext,
        AndroidFullMirrorPreviewWorkGateway(WorkManager.getInstance(context.applicationContext))
    )

    suspend fun enqueue(
        profile: AccountProfileEntity,
        includeContactNames: Boolean
    ): FullMirrorPreviewEnqueueResult {
        if (profile.connectionState != AccountProfileEntity.CONNECTION_STATE_CONNECTED) {
            return FullMirrorPreviewEnqueueResult.Blocked("Reconnect the selected Mirror account first.")
        }
        val database = DatabaseProvider.getDatabase(context)
        val settings = database.accountProfileDao().findSettings(profile.profileId)
        if (settings?.backupMode != GmailBackupMode.MIRROR.name) {
            return FullMirrorPreviewEnqueueResult.Blocked("Select a connected Mirror account first.")
        }
        val deviceStore = DeviceProfileStore.create(context)
        val device = deviceStore.getOrCreate()
        val label = deviceStore.getGmailDeviceLabelId(profile.profileId)
            ?: return FullMirrorPreviewEnqueueResult.Blocked(
                "No existing device-scoped Gmail label is bound to this Mirror profile."
            )
        val uniqueName = FullMirrorPreviewWorkContract.uniqueWorkName(profile.profileId, device.deviceId)
        gateway.activeUnique(uniqueName)?.let { info ->
            val scanId = FullMirrorPreviewWorkContract.scanId(info.tags)
                ?: database.mirrorPreviewScanDao()
                .findActive(profile.profileId, device.deviceId, System.currentTimeMillis())?.scanId
                ?: return FullMirrorPreviewEnqueueResult.Blocked("A Full Mirror preview is already active.")
            return FullMirrorPreviewEnqueueResult.AlreadyRunning(info.id, scanId)
        }
        val conflictingTags = listOf(
            GmailBackupWorkContract.ALL_WORK_TAG,
            FullMirrorWorkContract.ALL_WORK_TAG,
            BackupVerificationWorkContract.TAG
        )
        if (conflictingTags.any { gateway.activeByTag(it) != null }) {
            return FullMirrorPreviewEnqueueResult.Blocked("Another backup, verification, or Full Mirror operation is active.")
        }
        val dao = database.mirrorPreviewScanDao()
        val now = System.currentTimeMillis()
        dao.expireAbandoned(now)
        dao.deleteTerminalBefore(now - FullMirrorPreviewWorkContract.RETENTION_MILLIS)
        val existing = dao.findActive(profile.profileId, device.deviceId, now)
        if (
            existing?.lifecycleState == FullMirrorPreviewScanState.ENQUEUED.name &&
            existing.updatedAt >= now - ENQUEUE_RACE_GUARD_MILLIS
        ) {
            return FullMirrorPreviewEnqueueResult.Blocked(
                "A Full Mirror preview is already being queued."
            )
        }
        val scanSlotId = MirrorPreviewAccountBinding.digest(
            "preview-slot\u0000${profile.profileId}\u0000${device.deviceId}"
        )
        if (existing == null) dao.deleteReusableScanSlot(scanSlotId)
        val scan = existing ?: MirrorPreviewScanEntity(
            scanId = scanSlotId,
            profileId = profile.profileId,
            accountFingerprint = MirrorPreviewAccountBinding.fingerprint(
                profile.profileId,
                profile.accountEmail
            ),
            deviceId = device.deviceId,
            deviceLabelId = label,
            expectedPolicy = GmailBackupMode.MIRROR.name,
            includeContactNames = includeContactNames,
            lifecycleState = FullMirrorPreviewScanState.ENQUEUED.name,
            stage = FullMirrorPreviewStage.PREPARING.name,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + SCAN_TTL_MILLIS
        ).also {
            try {
                dao.insertScan(it)
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                return FullMirrorPreviewEnqueueResult.Blocked(
                    "A Full Mirror preview is already being queued."
                )
            }
            dao.supersedeOthers(it.profileId, it.deviceId, it.scanId, now)
        }
        val request = FullMirrorPreviewRequestFactory.create(scan, now)
        gateway.enqueueUnique(uniqueName, request)
        FullMirrorPreviewDiagnostics.enqueue(scan, request.id, continuation = existing != null)
        return FullMirrorPreviewEnqueueResult.Enqueued(request.id, scan.scanId)
    }

    fun observe() = gateway.observeAll()
    suspend fun cancel(workId: UUID, scanId: String) {
        DatabaseProvider.getDatabase(context).mirrorPreviewScanDao().cancelScan(
            scanId,
            System.currentTimeMillis(),
            FullMirrorFailureCategory.CANCELLED.name
        )
        gateway.cancel(workId)
        FullMirrorPreviewRecoveryState.clear(scanId)
    }

    companion object {
        const val SCAN_TTL_MILLIS = 24L * 60L * 60L * 1_000L
        const val ENQUEUE_RACE_GUARD_MILLIS = 5_000L
    }
}
