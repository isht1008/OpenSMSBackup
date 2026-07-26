package io.github.isht1008.opensmsbackup.gmail.backup

import android.content.Context
import android.util.Log
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
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
import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import java.util.Locale
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabelManager

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

                val allConversations =
                    conversationBuilder.build(
                        messages
                    )

                knownConversationTotal = allConversations.size

                val deviceStore = DeviceProfileStore.create(context)
                val deviceProfile = deviceStore.getOrCreate()
                val conversationScope = GmailBackupConversationLimiter.applyConversations(
                    orderedConversations = allConversations
                )
                val conversations = conversationScope.conversations
                val includedMessageTotal = conversations.sumOf { it.messages.size }
                val backupMode = MultiAccountRepository.create(context)
                    .getBackupMode(accountProfile.profileId)

                if (conversationScope.isLimitedTest) {
                    Log.i(
                        "OpenSMSBackup",
                        "TEST MODE: Selected ${conversations.size} most recently active conversations " +
                            "from ${allConversations.size} local conversations."
                    )
                    conversations.forEachIndexed { index, conversation ->
                        val canonical = SmsAddressNormalizer()
                            .normalize(conversation.address, deviceProfile.defaultRegion).canonical
                        Log.d(
                            "OpenSMSBackup",
                            "test_scope_position=${index + 1} " +
                                "identity=${GmailBackupConversationLimiter.safeIdentity(canonical)} " +
                                "messages=${conversation.messageCount} latest=${conversation.lastMessageDate}"
                        )
                    }
                }

                if (!GmailBackupTestModePolicy.supports(backupMode)) {
                    return@withContext Result.success(
                        GmailBackupCompletion(
                            state = GmailBackupCompletionState.FAILED_BEFORE_START,
                            checked = 0,
                            total = conversations.size,
                            uploaded = 0,
                            unchanged = 0,
                            failed = 0,
                            reason = "Limited test backups currently support Archive mode only.",
                            profileId = accountProfile.profileId,
                            accountEmail = trimmedEmail,
                            totalMessages = includedMessageTotal,
                            isLimitedTest = true,
                            sourceConversationTotal = allConversations.size,
                            sourceMessageTotal = messages.size
                        )
                    )
                }

                val gmailService =
                    GmailApiClient(context)
                        .createService(
                            accountProfile
                        )

                val labelManager = GmailLabelManager(
                    gmail = gmailService,
                    profileId = accountProfile.profileId,
                    onRetry = onRetry,
                    deviceProfile = deviceProfile,
                    cachedDeviceLabelId = deviceStore.getGmailDeviceLabelId(
                        accountProfile.profileId
                    ),
                    onDeviceLabelResolved = { labelId ->
                        deviceStore.setGmailDeviceLabelId(accountProfile.profileId, labelId)
                    }
                )
                val gmailLabels = labelManager.ensureLabels()
                val deviceLabelId = requireNotNull(gmailLabels.deviceConversations)

                val uploader =
                    GmailUploader(
                        gmail = gmailService,
                        profileId = accountProfile.profileId,
                        onRetry = onRetry,
                        labelManager = labelManager,
                        initialLabels = gmailLabels
                    )

                val archiveReader = GmailArchivedConversationReader(
                    gmail = gmailService,
                    profileId = accountProfile.profileId,
                    onRetry = onRetry
                )

                var previousSnapshotsTrashed = 0

                val mirrorStrategy = MirrorBackupStrategy(
                    uploader = uploader,
                    snapshotDao = snapshotDao,
                    accountId = accountId,
                    accountEmail = trimmedEmail,
                    canTrashPrevious = { messageId, conversation ->
                        archiveReader.read(messageId).getOrNull()?.let { document ->
                            DeviceSnapshotOwnership.matchesV2(
                                document,
                                trimmedEmail,
                                deviceProfile.deviceId,
                                deviceLabelId,
                                conversation,
                                deviceProfile.defaultRegion
                            )
                        } == true
                    },
                    onPreviousSnapshotTrashed = { messageId ->
                        previousSnapshotsTrashed++
                        Log.d(
                            "OpenSMSBackup",
                            "mirror_previous_snapshot_trashed message=${GmailBackupConversationLimiter.safeIdentity(messageId)}"
                        )
                    }
                )
                val archiveAppendStrategy =
                    ArchiveAppendBackupStrategy(
                        locator = GmailArchiveLocator(
                            lookup = archiveReader,
                            accountEmail = trimmedEmail,
                            deviceId = deviceProfile.deviceId,
                            deviceLabelId = deviceLabelId,
                            defaultRegion = deviceProfile.defaultRegion
                        ),
                        uploader = uploader,
                        snapshotDao = snapshotDao,
                        accountId = accountId,
                        accountEmail = trimmedEmail,
                        deviceProfile = deviceProfile
                    )
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

                    if (backupMode == GmailBackupMode.MIRROR && conversationScope.isLimitedTest) {
                        val canonical = SmsAddressNormalizer()
                            .normalize(conversation.address, deviceProfile.defaultRegion).canonical
                        Log.d(
                            "OpenSMSBackup",
                            "mirror_test_check identity=${GmailBackupConversationLimiter.safeIdentity(canonical)} " +
                                "messages=${conversation.messageCount} hash=${snapshotHash.take(12)} " +
                                "previous=${existingSnapshot?.gmailMessageId?.isNotBlank() == true} " +
                                "unchanged=${existingSnapshot?.snapshotHash == snapshotHash}"
                        )
                    }

                    if (
                        backupMode == GmailBackupMode.MIRROR &&
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

                        if (conversationScope.isLimitedTest) {
                            Log.d("OpenSMSBackup", "mirror_test_result unchanged=true uploaded=false")
                        }

                        continue
                    }

                    val email =
                        mimeMessageBuilder.build(
                            conversation = conversation,
                            accountEmail = trimmedEmail,
                            snapshotHash = snapshotHash,
                            deviceProfile = deviceProfile
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
                                        "but the previous snapshot could not be moved to Trash " +
                                        "(${failure.category}, status=${failure.httpStatusCode ?: "unavailable"})"
                                )
                            }
                        }
                    )

                    uploadResult
                        .onSuccess { gmailResult ->
                            if (gmailResult.wasUploaded) uploaded++ else skipped++
                            if (backupMode == GmailBackupMode.MIRROR && conversationScope.isLimitedTest) {
                                Log.d(
                                    "OpenSMSBackup",
                                    "mirror_test_result uploaded=${gmailResult.wasUploaded} " +
                                        "message=${GmailBackupConversationLimiter.safeIdentity(gmailResult.messageId)}"
                                )
                            }
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
                                        append(failure.category)
                                        append(" (status=")
                                        append(failure.httpStatusCode ?: "unavailable")
                                        append(')')
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

                if (GmailBackupTestModePolicy.shouldAdvanceFullBackupMetadata(
                        isLimitedTest = conversationScope.isLimitedTest,
                        aborted = abortState != null
                    )
                ) {
                    accountDao.insert(
                        accountDao.findById(accountId)
                            ?.copy(lastBackupTime = now, lastSyncTime = now)
                            ?: BackupAccountEntity(
                                accountId = accountId,
                                accountEmail = trimmedEmail,
                                lastBackupTime = now,
                                lastSyncTime = now,
                                isDefault = true
                            )
                    )
                }

                Result.success(
                    GmailBackupCompletion(
                        state = abortState ?: if (conversationScope.isLimitedTest) {
                            GmailBackupCompletionState.LIMITED_TEST_COMPLETED
                        } else {
                            GmailBackupCompletionState.COMPLETED
                        },
                        checked = checked,
                        total = conversations.size,
                        uploaded = uploaded,
                        unchanged = skipped,
                        failed = failed,
                        previousSnapshotsTrashed = previousSnapshotsTrashed,
                        reason = abortFailure?.userMessage ?: if (conversationScope.isLimitedTest) {
                            buildString {
                                if (backupMode == GmailBackupMode.MIRROR) {
                                    append("Mirror test completed for the 10 most recently active conversations: ")
                                } else {
                                    append("Archive test completed for the 10 most recently active conversations: ")
                                }
                                append("$checked checked, $uploaded replaced/uploaded, $skipped unchanged, ")
                                append("$previousSnapshotsTrashed previous snapshots moved to Trash, $failed failed.")
                            }
                        } else {
                            null
                        },
                        profileId = accountProfile.profileId,
                        accountEmail = trimmedEmail,
                        failure = abortFailure,
                        totalMessages = includedMessageTotal,
                        stoppedAtSafetyLimit = conversationScope.isLimitedTest,
                        isLimitedTest = conversationScope.isLimitedTest,
                        sourceConversationTotal = allConversations.size,
                        sourceMessageTotal = messages.size,
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
