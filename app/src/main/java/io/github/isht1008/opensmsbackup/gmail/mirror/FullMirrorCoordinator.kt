package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkContract
import kotlinx.coroutines.flow.first

class FullMirrorCoordinator(private val context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun confirmAndEnqueue(runId: String, typedConfirmation: String?): Result<java.util.UUID> = runCatching {
        val db = DatabaseProvider.getDatabase(context)
        val dao = db.mirrorReconciliationDao()
        val run = requireNotNull(dao.findRun(runId))
        require(run.status == FullMirrorRunStatus.PREVIEW.name) { "Preview was already confirmed or is no longer active." }
        val profile = requireNotNull(db.accountProfileDao().findProfileById(run.profileId))
        val settings = requireNotNull(db.accountProfileDao().findSettings(run.profileId))
        val device = DeviceProfileStore.create(context).getOrCreate()
        require(profile.accountEmail.trim().lowercase(java.util.Locale.ROOT) == run.accountIdentity)
        require(settings.backupMode == GmailBackupMode.MIRROR.name)
        require(device.deviceId == run.deviceId)
        require(run.localScanComplete && run.conflictCount == 0 && run.failedCount == 0)
        require(System.currentTimeMillis() <= run.expiresAt) { "Preview expired." }
        val trash = run.trashRemoteOnlyCount + run.replaceChangedCount
        if (trash > 0) require(typedConfirmation?.trim() == "MIRROR $trash") { "Typed Mirror confirmation does not match." }
        val gmailActive = workManager.getWorkInfosByTagFlow(GmailBackupWorkContract.ALL_WORK_TAG).first().any { !it.state.isFinished }
        val mirrorActive = workManager.getWorkInfosByTagFlow(FullMirrorWorkContract.ALL_WORK_TAG).first().any { !it.state.isFinished }
        val previewActive = workManager.getWorkInfosByTagFlow(FullMirrorPreviewWorkContract.ALL_WORK_TAG).first().any { !it.state.isFinished }
        require(!gmailActive && !mirrorActive && !previewActive) { "Another Gmail operation is active." }
        check(dao.confirm(runId, run.profileId, run.deviceId, System.currentTimeMillis()) == 1)
        val input = FullMirrorWorkInput(runId, run.profileId, run.deviceId, System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<FullMirrorWorker>()
            .setInputData(FullMirrorWorkContract.inputData(input))
            .addTag(FullMirrorWorkContract.ALL_WORK_TAG)
            .addTag(FullMirrorWorkContract.profileTag(run.profileId))
            .addTag(FullMirrorWorkContract.runTag(run.runId))
            .build()
        workManager.enqueueUniqueWork(
            FullMirrorWorkContract.uniqueWorkName(run.profileId, run.deviceId, runId),
            ExistingWorkPolicy.KEEP,
            request
        )
        request.id
    }


    suspend fun resume(runId: String): Result<java.util.UUID> = runCatching {
        val db = DatabaseProvider.getDatabase(context)
        val run = requireNotNull(db.mirrorReconciliationDao().findRun(runId))
        require(run.status in setOf(FullMirrorRunStatus.CANCELLED.name, FullMirrorRunStatus.COMPLETED_WITH_WARNINGS.name))
        val profile = requireNotNull(db.accountProfileDao().findProfileById(run.profileId))
        val settings = requireNotNull(db.accountProfileDao().findSettings(run.profileId))
        val device = DeviceProfileStore.create(context).getOrCreate()
        require(profile.accountEmail.trim().lowercase(java.util.Locale.ROOT) == run.accountIdentity)
        require(settings.backupMode == GmailBackupMode.MIRROR.name && device.deviceId == run.deviceId)
        enqueue(run.runId, run.profileId, run.deviceId)
    }

    private suspend fun enqueue(runId: String, profileId: String, deviceId: String): java.util.UUID {
        val gmailActive = workManager.getWorkInfosByTagFlow(GmailBackupWorkContract.ALL_WORK_TAG).first().any { !it.state.isFinished }
        val mirrorActive = workManager.getWorkInfosByTagFlow(FullMirrorWorkContract.ALL_WORK_TAG).first().any { !it.state.isFinished }
        val previewActive = workManager.getWorkInfosByTagFlow(FullMirrorPreviewWorkContract.ALL_WORK_TAG).first().any { !it.state.isFinished }
        require(!gmailActive && !mirrorActive && !previewActive) { "Another Gmail operation is active." }
        val input = FullMirrorWorkInput(runId, profileId, deviceId, System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<FullMirrorWorker>()
            .setInputData(FullMirrorWorkContract.inputData(input))
            .addTag(FullMirrorWorkContract.ALL_WORK_TAG)
            .addTag(FullMirrorWorkContract.profileTag(profileId))
            .addTag(FullMirrorWorkContract.runTag(runId)).build()
        workManager.enqueueUniqueWork(FullMirrorWorkContract.uniqueWorkName(profileId, deviceId, runId), ExistingWorkPolicy.KEEP, request)
        return request.id
    }
    fun observe() = workManager.getWorkInfosByTagFlow(FullMirrorWorkContract.ALL_WORK_TAG)

    suspend fun reconcileFailedConfirmedPlans(): List<FullMirrorFailedPlanReconciler.Outcome> =
        FullMirrorFailedPlanReconciler(context).reconcile(
            workManager.getWorkInfosByTagFlow(FullMirrorWorkContract.ALL_WORK_TAG).first()
        )

    suspend fun reconcileFailedConfirmedPlans(infos: List<WorkInfo>): List<FullMirrorFailedPlanReconciler.Outcome> =
        FullMirrorFailedPlanReconciler(context).reconcile(infos)
    fun cancel(workId: java.util.UUID) = workManager.cancelWorkById(workId)
    suspend fun hasActiveForProfile(profileId: String): Boolean =
        workManager.getWorkInfosByTagFlow(FullMirrorWorkContract.profileTag(profileId)).first()
            .any { it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED) } ||
            workManager.getWorkInfosByTagFlow(
                FullMirrorPreviewWorkContract.profileTag(profileId)
            ).first().any {
                it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
            }
}
