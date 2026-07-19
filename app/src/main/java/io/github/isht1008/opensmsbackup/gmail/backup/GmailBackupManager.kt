package io.github.isht1008.opensmsbackup.gmail.backup

import android.content.Context
import android.util.Log
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.BackupAccountEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploader
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.error.GmailErrorClassifier
import io.github.isht1008.opensmsbackup.gmail.error.GmailOperationException
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import java.util.Locale

class GmailBackupManager {

    private val smsRepository =
        SmsRepository()

    private val conversationBuilder =
        SmsConversationSnapshotBuilder()

    private val mimeMessageBuilder =
        ConversationMimeMessageBuilder()

    suspend fun backup(
        context: Context,
        accountProfile: AccountProfileEntity,
        includeContactNames: Boolean,
        maxConversations: Int? = null,
        onProgress: suspend (
            current: Int,
            total: Int,
            uploaded: Int,
            skipped: Int,
            failed: Int
        ) -> Unit,
        onRetry: suspend (attempt: Int, maximumAttempts: Int) -> Unit = { _, _ -> }
    ): Result<GmailBackupCompletion> {

        return withContext(Dispatchers.IO) {

            var knownMessageTotal = 0
            var knownConversationTotal = 0

            try {
                require(
                    accountProfile
                        .accountEmail
                        .isNotBlank()
                ) {
                    "Gmail account email cannot be blank."
                }

                require(
                    maxConversations == null ||
                            maxConversations > 0
                ) {
                    "Conversation limit must be greater than zero."
                }

                val trimmedEmail =
                    accountProfile
                        .accountEmail
                        .trim()

                val accountId =
                    trimmedEmail.lowercase(
                        Locale.ROOT
                    )

                val database =
                    DatabaseProvider.getDatabase(
                        context
                    )

                val accountDao =
                    database.backupAccountDao()

                val snapshotDao =
                    database.conversationSnapshotDao()

                val existingAccount =
                    accountDao.findById(
                        accountId
                    )

                accountDao.insert(
                    existingAccount
                        ?.copy(
                            accountEmail = trimmedEmail,
                            backupEnabled = true
                        )
                        ?: BackupAccountEntity(
                            accountId = accountId,
                            accountEmail = trimmedEmail,
                            isDefault = true,
                            backupEnabled = true
                        )
                )

                val messages =
                    smsRepository.getSmsMessages(
                        context = context,
                        includeContactNames =
                            includeContactNames
                    )

                knownMessageTotal = messages.size

                val conversations =
                    conversationBuilder.build(
                        messages
                    )

                knownConversationTotal = conversations.size

                val gmailService =
                    GmailApiClient(context)
                        .createService(
                            accountProfile
                        )

                val uploader =
                    GmailUploader(
                        gmail = gmailService,
                        profileId = accountProfile.profileId,
                        onRetry = onRetry
                    )

                val mirrorStrategy = MirrorBackupStrategy(
                    uploader = uploader,
                    snapshotDao = snapshotDao,
                    accountId = accountId,
                    accountEmail = trimmedEmail
                )
                val archiveAppendStrategy =
                    ArchiveAppendBackupStrategy(mirrorStrategy)
                val backupMode = MultiAccountRepository.create(context)
                    .getBackupMode(accountProfile.profileId)
                val backupStrategy = BackupStrategySelector(
                    mirrorStrategy = mirrorStrategy,
                    archiveAppendStrategy = archiveAppendStrategy
                ).select(backupMode)

                val classifier = GmailErrorClassifier()
                val circuitBreaker = GmailFailureCircuitBreaker()
                var abortFailure: io.github.isht1008.opensmsbackup.gmail.error.GmailFailure? = null
                var abortState: GmailBackupCompletionState? = null

                var checked = 0
                var uploaded = 0
                var skipped = 0
                var failed = 0
                var safetyLimitReached = false

                val failures =
                    mutableListOf<String>()

                val warnings =
                    mutableListOf<String>()

                onProgress(
                    checked,
                    conversations.size,
                    uploaded,
                    skipped,
                    failed
                )

                for (conversation in conversations) {

                    currentCoroutineContext()
                        .ensureActive()
                    yield()

                    if (
                        maxConversations != null &&
                        uploaded >= maxConversations
                    ) {
                        safetyLimitReached = true
                        break
                    }

                    val snapshotHash =
                        ConversationSnapshotHashGenerator
                            .generate(
                                conversation
                            )

                    val existingSnapshot =
                        snapshotDao.find(
                            accountId = accountId,
                            androidThreadId =
                                conversation.threadId
                        )

                    if (
                        existingSnapshot?.snapshotHash ==
                        snapshotHash
                    ) {
                        skipped++
                        checked++
                        circuitBreaker.recordSuccess()

                        onProgress(
                            checked,
                            conversations.size,
                            uploaded,
                            skipped,
                            failed
                        )

                        continue
                    }

                    val email =
                        mimeMessageBuilder.build(
                            conversation = conversation,
                            accountEmail = trimmedEmail,
                            snapshotHash = snapshotHash
                        )

                    val uploadResult = backupStrategy.execute(
                        conversation = conversation,
                        email = email,
                        snapshotHash = snapshotHash,
                        existingSnapshot = existingSnapshot,
                        onPreviousSnapshotTrashFailure = { error ->
                            val failure =
                                if (error is GmailOperationException) error.failure
                                else classifier.classify(error)

                            if (failure.reauthorizationRequired) {
                                GmailAccountManager(context)
                                    .markAuthorizationRequired(accountProfile)
                            }

                            if (failure.stopBackup) {
                                abortFailure = failure
                                abortState = GmailBackupCompletionState.ABORTED_FATAL
                            }

                            if (warnings.size < 20) {
                                warnings.add(
                                    "Thread ${conversation.threadId}: new snapshot uploaded, " +
                                        "but the previous snapshot could not be moved to Trash" +
                                        error.message?.takeIf { it.isNotBlank() }
                                            ?.let { " - $it" }.orEmpty()
                                )
                            }
                        }
                    )

                    uploadResult
                        .onSuccess { gmailResult ->

                            uploaded++
                            circuitBreaker.recordSuccess()
                        }
                        .onFailure { error ->
                            failed++

                            val failure =
                                if (error is GmailOperationException) error.failure
                                else classifier.classify(error)

                            Log.w(
                                "OpenSMSBackup",
                                "gmail_operation=upload_conversation profile=${accountProfile.profileId.take(8)} " +
                                    "status=${failure.httpStatusCode} reason=${failure.googleReason} " +
                                    "category=${failure.category} retryable=${failure.retryable}"
                            )

                            if (failure.reauthorizationRequired) {
                                GmailAccountManager(context)
                                    .markAuthorizationRequired(accountProfile)
                            }

                            if (circuitBreaker.recordFailure(failure)) {
                                abortFailure = failure
                                abortState =
                                    if (failure.stopBackup) {
                                        GmailBackupCompletionState.ABORTED_FATAL
                                    } else {
                                        GmailBackupCompletionState.ABORTED_REPEATED_FAILURES
                                    }
                            }

                            if (failures.size < 20) {
                                failures.add(
                                    buildString {
                                        append(
                                            "Thread ${conversation.threadId}"
                                        )

                                        append(": ")
                                        append(
                                            error.javaClass.simpleName
                                        )
                                        append(" - ")
                                        append(
                                            error.message
                                                ?: "Unknown error"
                                        )
                                    }
                                )
                            }
                        }

                    checked++

                    onProgress(
                        checked,
                        conversations.size,
                        uploaded,
                        skipped,
                        failed
                    )

                    if (abortState != null) {
                        Log.w(
                            "OpenSMSBackup",
                            "gmail_early_abort profile=${accountProfile.profileId.take(8)} " +
                                "state=$abortState checked=$checked failed=$failed " +
                                "category=${abortFailure?.category} reason=${abortFailure?.googleReason}"
                        )
                        break
                    }
                }

                val now =
                    System.currentTimeMillis()

                accountDao.insert(
                    accountDao.findById(accountId)
                        ?.copy(
                            lastBackupTime = now,
                            lastSyncTime = now
                        )
                        ?: BackupAccountEntity(
                            accountId = accountId,
                            accountEmail = trimmedEmail,
                            lastBackupTime = now,
                            lastSyncTime = now,
                            isDefault = true
                        )
                )

                Result.success(
                    GmailBackupCompletion(
                        state = abortState ?: GmailBackupCompletionState.COMPLETED,
                        checked = checked,
                        total = conversations.size,
                        uploaded = uploaded,
                        unchanged = skipped,
                        failed = failed,
                        reason = abortFailure?.userMessage,
                        profileId = accountProfile.profileId,
                        accountEmail = trimmedEmail,
                        failure = abortFailure,
                        totalMessages = messages.size,
                        stoppedAtSafetyLimit =
                            safetyLimitReached,
                        failures = failures,
                        warnings = warnings
                    )
                )

            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val failure =
                    if (error is GmailOperationException) error.failure
                    else GmailErrorClassifier().classify(error)

                if (failure.reauthorizationRequired) {
                    GmailAccountManager(context)
                        .markAuthorizationRequired(accountProfile)
                }

                Result.success(
                    GmailBackupCompletion(
                        state = GmailBackupCompletionState.FAILED_BEFORE_START,
                        checked = 0,
                        total = knownConversationTotal,
                        uploaded = 0,
                        unchanged = 0,
                        failed = 0,
                        reason = failure.userMessage,
                        profileId = accountProfile.profileId,
                        accountEmail = accountProfile.accountEmail,
                        failure = failure,
                        totalMessages = knownMessageTotal
                    )
                )
            }
        }
    }
}
