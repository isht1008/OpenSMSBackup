package io.github.isht1008.opensmsbackup.gmail.mirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class FullMirrorPreviewWorker(
    appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val input = FullMirrorPreviewWorkContract.readInput(inputData) ?: return Result.failure()
        val dao = DatabaseProvider.getDatabase(applicationContext).mirrorPreviewScanDao()
        val scan = dao.findScan(input.scanId) ?: return Result.failure()
        FullMirrorPreviewDiagnostics.workerStart(
            input.scanId,
            id,
            runAttemptCount,
            workerParameters.generation
        )
        if (scan.lifecycleState == FullMirrorPreviewScanState.PUBLISHED.name) {
            val runId = scan.publishedRunId ?: return Result.failure()
            FullMirrorPreviewDiagnostics.terminal(scan, "PUBLISHED_EXISTING", runId)
            return Result.success(
                FullMirrorPreviewWorkContract.outputData(FullMirrorPreviewWorkResult(
                    input.scanId,
                    runId,
                    FullMirrorPreviewScanState.PUBLISHED
                ))
            )
        }
        if (
            scan.profileId != input.profileId ||
            scan.deviceId != input.deviceId ||
            scan.createdAt != input.createdAt ||
            scan.lifecycleState in setOf(
                FullMirrorPreviewScanState.CANCELLED.name,
                FullMirrorPreviewScanState.EXPIRED.name,
                FullMirrorPreviewScanState.SUPERSEDED.name,
                FullMirrorPreviewScanState.PUBLISHED.name
            )
        ) return Result.failure()
        val foreground = FullMirrorPreviewForegroundInfoFactory(applicationContext)
        return try {
            setForeground(foreground.create(id, FullMirrorPreviewProgress(
                input.scanId,
                FullMirrorPreviewStage.PREPARING
            )))
            FullMirrorPreviewDiagnostics.foreground(input.scanId, id)
            when (val outcome = FullMirrorPreviewScanService(applicationContext).run(input.scanId) {
                progress ->
                setProgress(FullMirrorPreviewWorkContract.progressData(progress))
                setForeground(foreground.create(id, progress))
            }) {
                is FullMirrorPreviewScanOutcome.Published ->
                    Result.success(FullMirrorPreviewWorkContract.outputData(FullMirrorPreviewWorkResult(
                        input.scanId,
                        outcome.runId,
                        FullMirrorPreviewScanState.PUBLISHED
                    )))
                is FullMirrorPreviewScanOutcome.Retry -> {
                    dao.findScan(input.scanId)?.let { current ->
                        val progress = current.toProgress()
                        setProgress(FullMirrorPreviewWorkContract.progressData(progress))
                        setForeground(foreground.create(id, progress))
                    }
                    Result.retry()
                }
                is FullMirrorPreviewScanOutcome.Failed -> Result.failure(
                    FullMirrorPreviewWorkContract.outputData(FullMirrorPreviewWorkResult(
                        input.scanId,
                        null,
                        FullMirrorPreviewScanState.FAILED,
                        outcome.category
                    ))
                )
                FullMirrorPreviewScanOutcome.Expired -> Result.failure(
                    FullMirrorPreviewWorkContract.outputData(FullMirrorPreviewWorkResult(
                        input.scanId,
                        null,
                        FullMirrorPreviewScanState.EXPIRED,
                        FullMirrorFailureCategory.EXPIRED.name
                    ))
                )
            }
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) {
                val current = dao.findScan(input.scanId)
                val explicit = FullMirrorPreviewInterruptionPolicy.isExplicitCancellation(current)
                if (FullMirrorPreviewInterruptionPolicy.shouldCheckpoint(current)) {
                    dao.markInterruptedIfResumable(
                        input.scanId,
                        System.currentTimeMillis(),
                        "WORK_INTERRUPTED"
                    )
                }
                FullMirrorPreviewDiagnostics.interrupted(input.scanId, stopReason, explicit)
            }
            throw cancel
        } catch (_: Throwable) {
            withContext(NonCancellable) {
                dao.findScan(input.scanId)?.let {
                    if (it.lifecycleState == FullMirrorPreviewScanState.PUBLISHED.name &&
                        it.publishedRunId != null
                    ) {
                        return@withContext
                    }
                    dao.updateScan(it.copy(
                        lifecycleState = FullMirrorPreviewScanState.FAILED.name,
                        stage = FullMirrorPreviewStage.PAUSED.name,
                        updatedAt = System.currentTimeMillis(),
                        lastErrorCategory = "WORKER_INFRASTRUCTURE"
                    ))
                }
            }
            dao.findScan(input.scanId)?.takeIf {
                it.lifecycleState == FullMirrorPreviewScanState.PUBLISHED.name &&
                    it.publishedRunId != null
            }?.let {
                return Result.success(
                    FullMirrorPreviewWorkContract.outputData(FullMirrorPreviewWorkResult(
                        input.scanId,
                        it.publishedRunId,
                        FullMirrorPreviewScanState.PUBLISHED
                    ))
                )
            }
            Result.failure(FullMirrorPreviewWorkContract.outputData(
                FullMirrorPreviewWorkResult(
                    input.scanId,
                    null,
                    FullMirrorPreviewScanState.FAILED,
                    "WORKER_INFRASTRUCTURE"
                )
            ))
        }
    }

    private fun io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity.toProgress() =
        toPreviewProgress()
}

internal class FullMirrorPreviewForegroundInfoFactory(private val context: Context) {
    fun create(
        workId: java.util.UUID,
        progress: FullMirrorPreviewProgress
    ): ForegroundInfo {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "OpenSMSBackup Full Mirror Preview",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(io.github.isht1008.opensmsbackup.R.drawable.ic_launcher_foreground)
            .setContentTitle("Full Mirror preview")
            .setContentText(FullMirrorPreviewProgressText.title(progress.stage))
            .setProgress(
                progress.fullReadsRequired.coerceAtLeast(1),
                progress.fullReadsCompleted.coerceAtMost(progress.fullReadsRequired),
                progress.fullReadsRequired == 0
            )
            .setOngoing(true)
            .addAction(
                0,
                "Cancel",
                FullMirrorPreviewCancelReceiver.pendingIntent(
                    context,
                    workId,
                    progress.scanId
                )
            )
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val SERVICE_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        private const val CHANNEL = "opensmsbackup-full-mirror-preview"
        private const val NOTIFICATION_ID = 4103
    }
}

class FullMirrorPreviewCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workId = intent.getStringExtra(EXTRA_WORK_ID)
            ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() } ?: return
        val scanId = intent.getStringExtra(EXTRA_SCAN_ID)?.takeIf(String::isNotBlank) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FullMirrorPreviewCoordinator(context.applicationContext).cancel(workId, scanId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_WORK_ID = "preview.cancel.work_id"
        private const val EXTRA_SCAN_ID = "preview.cancel.scan_id"

        fun pendingIntent(context: Context, workId: UUID, scanId: String): PendingIntent {
            val intent = Intent(context, FullMirrorPreviewCancelReceiver::class.java)
                .putExtra(EXTRA_WORK_ID, workId.toString())
                .putExtra(EXTRA_SCAN_ID, scanId)
            return PendingIntent.getBroadcast(
                context,
                workId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
