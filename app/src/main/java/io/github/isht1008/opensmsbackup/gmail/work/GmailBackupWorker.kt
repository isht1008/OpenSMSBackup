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
import kotlinx.coroutines.CancellationException

class GmailBackupWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {

    private val notificationFactory = GmailBackupNotificationFactory(appContext)
    private val shouldPromoteToForeground =
        GmailBackupForegroundPolicy.shouldPromote(appContext)

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

        var ownsSession = false
        var latest = progress(
            GmailBackupPhase.PREPARING,
            profile.profileId,
            profile.accountEmail,
            "Preparing backup"
        )

        try {
            Log.i("OpenSMSBackup", "gmail_worker_start work=$id profile=${profile.profileId.take(8)}")
            promoteToForeground(latest)
            setProgress(GmailBackupWorkContract.progressData(latest))
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

            val completion = GmailBackupManager().backup(
                context = applicationContext,
                accountProfile = profile,
                includeContactNames = input.includeContactNames,
                maxConversations = input.maximumConversations,
                onProgress = { checked, total, uploaded, unchanged, failed ->
                    latest = GmailBackupWorkProgress(
                        phase = GmailBackupPhase.RUNNING,
                        profileId = profile.profileId,
                        accountEmail = profile.accountEmail,
                        checked = checked,
                        total = total,
                        uploaded = uploaded,
                        unchanged = unchanged,
                        failed = failed,
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

            latest = latest.copy(
                phase = GmailBackupPhase.FINALIZING,
                statusMessage = "Finalizing backup"
            )
            publish(latest)
            Log.i("OpenSMSBackup", "gmail_worker_terminal work=$id state=${completion.state}")
            return Result.success(GmailBackupWorkContract.outputData(completion))
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
        setProgress(GmailBackupWorkContract.progressData(progress))
        promoteToForeground(progress)
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
}
