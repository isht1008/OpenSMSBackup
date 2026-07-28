package io.github.isht1008.opensmsbackup.gmail.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupManager
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupSession
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GmailBackupWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {

    private val notificationFactory = GmailBackupNotificationFactory(appContext)
    private val shouldPromoteToForeground =
        GmailBackupForegroundPolicy.shouldPromote(appContext)
    private val publishMutex = Mutex()
    private lateinit var etaEstimator: GmailBackupEtaEstimator
    private var currentProgress: GmailBackupWorkProgress? = null
    private var workerStartedEpoch: Long = 0L

    override suspend fun doWork(): Result {
        val input = GmailBackupWorkContract.readInput(inputData)
            ?: return Result.failure(
                GmailBackupWorkContract.outputData(
                    failedBeforeStart("Gmail backup work input is missing or invalid.")
                )
            )

        val accountManager = GmailAccountManager(applicationContext)
        val profile = accountManager.getAccountProfile(input.profileId)
        val preflightFailure = GmailBackupWorkerPreflight.failureFor(profile)
        if (profile == null) {
            return Result.failure(
                GmailBackupWorkContract.outputData(requireNotNull(preflightFailure))
            )
        }
        if (preflightFailure != null) {
            return Result.success(GmailBackupWorkContract.outputData(preflightFailure))
        }

        val workerStartedElapsed = android.os.SystemClock.elapsedRealtime()
        workerStartedEpoch = System.currentTimeMillis()
        var ownsSession = false
        var latest = progress(
            GmailBackupPhase.PREPARING,
            profile.profileId,
            profile.accountEmail,
            "Preparing backup"
        )
        etaEstimator = GmailBackupEtaEstimator(
            startedElapsedRealtime = workerStartedElapsed,
            nowElapsedRealtime = android.os.SystemClock::elapsedRealtime
        )

        try {
            Log.i("OpenSMSBackup", "gmail_worker_start work=$id profile=${profile.profileId.take(8)}")
            publish(latest)
            Log.i(
                "OpenSMSBackup",
                "gmail_worker_foreground work=$id enabled=$shouldPromoteToForeground"
            )

            ownsSession = GmailBackupSession.begin(profile.profileId)
            if (!ownsSession) {
                return Result.success(
                    GmailBackupWorkContract.outputData(
                        failedBeforeStart(
                            "Another Gmail backup is already active.",
                            profile.profileId,
                            profile.accountEmail
                        )
                    )
                )
            }

            val ticker = CoroutineScope(currentCoroutineContext()).launch {
                while (true) {
                    delay(15_000L)
                    currentProgress?.let { publish(it) }
                }
            }
            val completion = try {
                GmailBackupManager().backup(
                context = applicationContext,
                accountProfile = profile,
                includeContactNames = input.includeContactNames,
                backupScope = input.backupScope,
                onCheckpointSummary = { locallyUnchanged ->
                    latest = latest.copy(
                        locallyUnchanged = locallyUnchanged,
                        checked = locallyUnchanged,
                        unchanged = locallyUnchanged,
                        statusMessage = "Checked local checkpoints"
                    )
                    publish(latest)
                },
                onCheckpointDetails = { details ->
                    latest = latest.copy(
                        legacyLocallyInitialized = details.locallyBootstrappable.size,
                        statusMessage =
                            "Local checkpoints: ${details.locallyUnchanged.size} matching, " +
                                "${details.uninitialized} uninitialized, " +
                                "${details.deviceMismatched} device-mismatched, " +
                                "${details.hashMismatched} changed, " +
                                "${details.missingCachedGmailId} missing cached IDs, " +
                                "${details.locallyBootstrappable.size} initialized locally"
                    )
                    publish(latest)
                },
                onRemoteRecovery = { count ->
                    latest = latest.copy(
                        remoteRecoveries = count,
                        statusMessage = "Recovering Gmail archive index"
                    )
                    publish(latest)
                },
                onStage = { stage ->
                    val phase = when (stage) {
                        GmailBackupStage.READING_LOCAL_SMS,
                        GmailBackupStage.BUILDING_LOCAL_CONVERSATIONS ->
                            GmailBackupPhase.READING
                        GmailBackupStage.CHECKING_LOCAL_CHECKPOINTS ->
                            GmailBackupPhase.CHECKING_LOCAL
                        GmailBackupStage.RECOVERING_GMAIL_INDEX ->
                            GmailBackupPhase.INDEXING
                        GmailBackupStage.COMPARING_CHANGED_CONVERSATIONS ->
                            GmailBackupPhase.COMPARING
                        GmailBackupStage.UPLOADING_CHANGED_CONVERSATIONS ->
                            GmailBackupPhase.UPLOADING
                        GmailBackupStage.COMPLETING ->
                            GmailBackupPhase.FINALIZING
                    }
                    latest = latest.copy(
                        phase = phase,
                        statusMessage = stage.displayName()
                    )
                    publish(latest)
                },
                onIndexProgress = { scanned, accepted ->
                    latest = latest.copy(
                        phase = GmailBackupPhase.INDEXING,
                        indexedMessages = scanned,
                        acceptedIndexMessages = accepted,
                        statusMessage = "Indexing existing Archive snapshots"
                    )
                    publish(latest)
                },
                onProgress = { checked, total, uploaded, unchanged, failed ->
                    latest = latest.copy(
                        checked = checked,
                        total = total,
                        uploaded = uploaded,
                        unchanged = unchanged,
                        failed = failed,
                        remotelyCompared =
                            (checked - latest.locallyUnchanged).coerceAtLeast(0),
                        remotelyUnchanged =
                            (unchanged - latest.locallyUnchanged).coerceAtLeast(0),
                        statusMessage = "Checking conversation $checked of $total"
                    )
                    publish(latest)
                },
                onRetry = { attempt, maximum ->
                    latest = latest.copy(
                        phase = GmailBackupPhase.RETRYING,
                        retryAttempt = attempt,
                        retryMaximum = maximum,
                        statusMessage = "Temporary Gmail error. Retrying $attempt of $maximum"
                    )
                    publish(latest)
                }
                ).getOrElse { error ->
                return Result.failure(
                    GmailBackupWorkContract.outputData(
                        failedBeforeStart(
                            error.message ?: "Unexpected Gmail backup worker failure.",
                            profile.profileId,
                            profile.accountEmail
                        )
                    )
                )
                }
            } finally {
                ticker.cancelAndJoin()
            }

            latest = latest.copy(
                phase = GmailBackupPhase.FINALIZING,
                statusMessage = "Finalizing backup"
            )
            publish(latest)
            val finalEstimate = etaEstimator.update(
                phase = GmailBackupPhase.FINALIZING,
                checked = completion.checked,
                total = completion.total,
                terminal = true
            )
            val finalCompletion = completion.copy(durationMillis = finalEstimate.elapsedMillis)
            Log.i("OpenSMSBackup", "gmail_worker_terminal work=$id state=${completion.state}")
            return Result.success(GmailBackupWorkContract.outputData(finalCompletion))
        } catch (cancellation: CancellationException) {
            Log.i("OpenSMSBackup", "gmail_worker_cancelled work=$id")
            throw cancellation
        } catch (error: Exception) {
            Log.e(
                "OpenSMSBackup",
                "gmail_worker_infrastructure_failure work=$id type=${error::class.java.simpleName}"
            )
            return Result.failure(
                GmailBackupWorkContract.outputData(
                    failedBeforeStart(
                        "Unexpected Gmail backup worker failure.",
                        profile.profileId,
                        profile.accountEmail
                    )
                )
            )
        } finally {
            if (ownsSession) {
                GmailBackupSession.end(profile.profileId)
            }
            Log.i("OpenSMSBackup", "gmail_worker_cleanup work=$id")
        }
    }

    private suspend fun publish(progress: GmailBackupWorkProgress) {
        publishMutex.withLock {
            val estimate = etaEstimator.update(
                phase = progress.phase,
                checked = progress.checked,
                total = progress.total
            )
            val enriched = progress.copy(
                approximateEtaSeconds = estimate.remainingSeconds,
                elapsedMillis = estimate.elapsedMillis,
                startedAtEpochMillis = workerStartedEpoch
            )
            currentProgress = enriched
            setProgress(GmailBackupWorkContract.progressData(enriched))
            promoteToForeground(enriched)
        }
    }

    private suspend fun promoteToForeground(progress: GmailBackupWorkProgress) {
        if (shouldPromoteToForeground) {
            setForeground(notificationFactory.foregroundInfo(id, progress))
        }
    }

    private fun progress(
        phase: GmailBackupPhase,
        profileId: String,
        email: String,
        message: String
    ) = GmailBackupWorkProgress(
        phase = phase,
        profileId = profileId,
        requestId = GmailBackupWorkContract.readInput(inputData)?.requestId,
        accountEmail = email,
        statusMessage = message
    )

    private fun failedBeforeStart(
        reason: String,
        profileId: String? = null,
        email: String? = null
    ) = GmailBackupCompletion(
        state = GmailBackupCompletionState.FAILED_BEFORE_START,
        checked = 0,
        total = 0,
        uploaded = 0,
        unchanged = 0,
        failed = 0,
        reason = reason,
        profileId = profileId,
        accountEmail = email
    )

    private fun GmailBackupStage.displayName(): String = when (this) {
        GmailBackupStage.READING_LOCAL_SMS -> "Reading local SMS"
        GmailBackupStage.BUILDING_LOCAL_CONVERSATIONS -> "Building local conversations"
        GmailBackupStage.CHECKING_LOCAL_CHECKPOINTS -> "Checking local checkpoints"
        GmailBackupStage.RECOVERING_GMAIL_INDEX -> "Recovering Gmail archive index"
        GmailBackupStage.COMPARING_CHANGED_CONVERSATIONS -> "Comparing changed conversations"
        GmailBackupStage.UPLOADING_CHANGED_CONVERSATIONS -> "Uploading changed conversations"
        GmailBackupStage.COMPLETING -> "Completing backup"
    }
}
