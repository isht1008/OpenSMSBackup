package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import kotlinx.coroutines.CancellationException

class FullMirrorWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val input = FullMirrorWorkContract.readInput(inputData) ?: return Result.failure()
        val db = DatabaseProvider.getDatabase(applicationContext)
        val dao = db.mirrorReconciliationDao()
        val run = dao.findRun(input.runId) ?: return Result.failure()
        if (run.profileId != input.profileId || run.deviceId != input.deviceId) return Result.failure()
        val device = DeviceProfileStore.create(applicationContext).getOrCreate()
        if (device.deviceId != input.deviceId) return Result.failure()
        val profile = db.accountProfileDao().findProfileById(input.profileId) ?: return Result.failure()
        if (!profile.accountEmail.equals(run.accountIdentity, ignoreCase = true)) return Result.failure()
        val maskedAccount = maskMirrorAccount(profile.accountEmail)
        val started = android.os.SystemClock.elapsedRealtime()
        val total = run.unchangedCount + run.uploadNewCount + run.replaceChangedCount +
            run.trashRemoteOnlyCount + run.recoverCacheCount
        val foreground = FullMirrorForegroundInfoFactory(applicationContext)
        val foregroundStarted = FullMirrorForegroundStartBoundary.enter(
            startForeground = { setForeground(foreground.create(id, 0, total)) },
            recordRejectedStart = {
                val terminalized = dao.failConfirmedForegroundStartIfAllPending(
                    run.runId,
                    run.profileId,
                    System.currentTimeMillis()
                ) == 1
                Log.e(
                    "OpenSMSBackup",
                    if (terminalized) "full_mirror_foreground_start_failed terminalized=true"
                    else "full_mirror_foreground_start_failed terminalized=false recovery_review_required=true"
                )
            }
        )
        if (!foregroundStarted) return Result.failure(failureOutput(input, run, maskedAccount, started, total))

        return try {
            val result = FullMirrorExecutionService(applicationContext).execute(input.runId) { summary ->
                val elapsed = android.os.SystemClock.elapsedRealtime() - started
                val checked = summary.completed + summary.failed
                val eta = if (checked >= 2 && summary.remaining > 0) {
                    ((elapsed.toDouble() / checked) * summary.remaining / 1_000.0).toLong().coerceAtLeast(1)
                } else null
                setProgress(FullMirrorWorkContract.progressData(FullMirrorWorkProgress(
                    runId = input.runId, profileId = input.profileId, maskedAccount = maskedAccount, checked = checked, total = total,
                    unchanged = run.unchangedCount, recoveries = run.recoverCacheCount, conflicts = run.conflictCount,
                    newUploaded = summary.newUploaded, changedReplaced = summary.changedReplaced,
                    previousTrashed = summary.previousTrashed, remoteOnlyTrashed = summary.remoteOnlyTrashed,
                    warnings = summary.warnings, failed = summary.failed, remaining = summary.remaining,
                    elapsedMillis = elapsed, etaSeconds = eta, phase = "Synchronizing approved Mirror plan"
                )))
                setForeground(foreground.create(id, summary.completed + summary.failed, total))
            }.getOrThrow()
            val duration = android.os.SystemClock.elapsedRealtime() - started
            val state = if (result.failed > 0) FullMirrorRunStatus.FAILED else if (result.warnings > 0) FullMirrorRunStatus.COMPLETED_WITH_WARNINGS else FullMirrorRunStatus.COMPLETED
            val output = FullMirrorWorkContract.outputData(FullMirrorWorkResult(
                runId = input.runId, profileId = input.profileId, maskedAccount = maskedAccount, state = state.name,
                durationMillis = duration, previewAgeMillis = (input.createdAt - run.createdAt).coerceAtLeast(0),
                ownedRemote = run.ownedRemoteConversations, unchanged = run.unchangedCount,
                newUploaded = result.newUploaded, changedReplaced = result.changedReplaced,
                previousTrashed = result.previousTrashed, remoteOnlyTrashed = result.remoteOnlyTrashed,
                warnings = result.warnings, recoveries = run.recoverCacheCount, conflicts = run.conflictCount, failed = result.failed,
                remaining = result.remaining, resumable = result.warnings > 0
            ))
            if (result.failed > 0) Result.failure(output) else Result.success(output)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            Result.failure()
        }
    }

    private fun failureOutput(
        input: FullMirrorWorkInput,
        run: io.github.isht1008.opensmsbackup.database.MirrorReconciliationRunEntity,
        maskedAccount: String,
        started: Long,
        total: Int
    ) = FullMirrorWorkContract.outputData(FullMirrorWorkResult(
        runId = input.runId,
        profileId = input.profileId,
        maskedAccount = maskedAccount,
        state = FullMirrorRunStatus.FAILED.name,
        durationMillis = android.os.SystemClock.elapsedRealtime() - started,
        previewAgeMillis = (input.createdAt - run.createdAt).coerceAtLeast(0),
        ownedRemote = run.ownedRemoteConversations,
        unchanged = run.unchangedCount,
        newUploaded = 0,
        changedReplaced = 0,
        previousTrashed = 0,
        remoteOnlyTrashed = 0,
        warnings = 0,
        recoveries = 0,
        conflicts = run.conflictCount,
        failed = 0,
        remaining = total,
        resumable = false
    ))
}