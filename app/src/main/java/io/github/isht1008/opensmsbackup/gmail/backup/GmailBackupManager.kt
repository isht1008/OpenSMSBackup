package io.github.isht1008.opensmsbackup.gmail.backup

import android.content.Context
import android.util.Log
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.BackupAccountEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.LocalSourceCheckpointUpdate
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
import java.util.concurrent.atomic.AtomicInteger
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabelManager

class GmailBackupManager {

    internal inline fun <T> withExecutableRun(
        plan: GmailBackupRunPlan,
        onBlocked: (String) -> T,
        execute: (List<SmsConversationSnapshot>) -> T
    ): T =
        plan.blockedReason?.let(onBlocked) ?: execute(plan.conversations)

    internal suspend fun advanceFullBackupMetadataIfEligible(
        scope: GmailBackupScope,
        abortState: GmailBackupCompletionState?,
        failed: Int,
        advance: suspend () -> Unit
    ) {
        if (
            GmailBackupRunPlanner.shouldAdvanceFullBackupMetadata(
                scope = scope,
                abortState = abortState,
                failed = failed
            )
        ) {
            advance()
        }
    }

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
        backupScope: GmailBackupScope,
        onProgress: suspend (
            current: Int,
            total: Int,
            uploaded: Int,
            skipped: Int,
            failed: Int
        ) -> Unit,
        onIndexProgress: suspend (scanned: Int, accepted: Int) -> Unit = { _, _ -> },
        onStage: suspend (GmailBackupStage) -> Unit = {},
        onCheckpointSummary: suspend (locallyUnchanged: Int) -> Unit = {},
        onCheckpointDetails: suspend (ArchiveCheckpointClassification) -> Unit = {},
        onRemoteRecovery: suspend (count: Int) -> Unit = {},
        onRetry: suspend (attempt: Int, maximumAttempts: Int) -> Unit = { _, _ -> }
    ): Result<GmailBackupCompletion> {

        return withContext(Dispatchers.IO) {

            val runStartedElapsed = android.os.SystemClock.elapsedRealtime()
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

                onStage(GmailBackupStage.READING_LOCAL_SMS)
                val smsReadStarted = android.os.SystemClock.elapsedRealtime()
                val messages =
                    smsRepository.getSmsMessages(
                        context = context,
                        includeContactNames =
                            includeContactNames
                    )
                val smsReadDuration =
                    android.os.SystemClock.elapsedRealtime() - smsReadStarted

                knownMessageTotal = messages.size

                onStage(GmailBackupStage.BUILDING_LOCAL_CONVERSATIONS)
                val conversationBuildStarted = android.os.SystemClock.elapsedRealtime()
                val allConversations =
                    conversationBuilder.build(
                        messages
                    )
                val conversationBuildDuration =
                    android.os.SystemClock.elapsedRealtime() - conversationBuildStarted

                knownConversationTotal = allConversations.size

                val backupMode = MultiAccountRepository.create(context)
                    .getBackupMode(accountProfile.profileId)
                val runPlan = GmailBackupRunPlanner.create(
                    allConversations = allConversations,
                    scope = backupScope,
                    mode = backupMode
                )
                val includedMessageTotal =
                    runPlan.conversations.sumOf { it.messages.size }
                val conversations = withExecutableRun(
                    plan = runPlan,
                    onBlocked = { reason ->
                        return@withContext Result.success(
                            GmailBackupCompletion(
                                state = GmailBackupCompletionState.FAILED_BEFORE_START,
                                checked = 0,
                                total = runPlan.conversations.size,
                                uploaded = 0,
                                unchanged = 0,
                                failed = 0,
                                reason = reason,
                                profileId = accountProfile.profileId,
                                accountEmail = trimmedEmail,
                                totalMessages = includedMessageTotal,
                                isLimitedTest = false,
                                sourceConversationTotal = runPlan.sourceConversationCount,
                                sourceMessageTotal = messages.size
                            )
                        )
                    },
                    execute = { it }
                )

                val deviceStore = DeviceProfileStore.create(context)
                val deviceProfile = deviceStore.getOrCreate()

                onStage(GmailBackupStage.CHECKING_LOCAL_CHECKPOINTS)
                val classificationStarted = android.os.SystemClock.elapsedRealtime()
                val snapshotsByThreadId = snapshotDao.findAllForAccount(accountId)
                    .associateBy { it.androidThreadId }
                val checkpointClassification =
                    if (
                        ArchiveLocalCheckpointClassifier.isFastPathEligible(
                            runPlan.scope,
                            backupMode
                        )
                    ) {
                        ArchiveLocalCheckpointClassifier.classify(
                            conversations,
                            snapshotsByThreadId,
                            accountId,
                            trimmedEmail,
                            deviceProfile.deviceId,
                            existingAccount?.lastBackupTime ?: 0L
                        )
                    } else {
                        ArchiveCheckpointClassification(
                            locallyUnchanged = emptyList(),
                            requiringComparison = conversations.map { conversation ->
                                ArchiveConversationCheckpoint(
                                    conversation = conversation,
                                    localSourceHash =
                                        LocalConversationSourceHashGenerator.generate(conversation),
                                    existingSnapshot = snapshotsByThreadId[conversation.threadId]
                                )
                            }
                        )
                    }
                val conversationsToCompare =
                    checkpointClassification.requiringComparison.map { it.conversation }
                val localHashesByThreadId =
                    checkpointClassification.requiringComparison.associate {
                        it.conversation.threadId to it.localSourceHash
                    }
                val classificationDuration =
                    android.os.SystemClock.elapsedRealtime() - classificationStarted
                val localCheckpointWrites =
                    checkpointClassification.requiringLocalHashUpgrade +
                        checkpointClassification.locallyBootstrappable
                val bootstrapStarted = android.os.SystemClock.elapsedRealtime()
                if (localCheckpointWrites.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val updated = snapshotDao.updateLocalSourceCheckpoints(
                        localCheckpointWrites.map { checkpoint ->
                            LocalSourceCheckpointUpdate(
                                id = requireNotNull(checkpoint.existingSnapshot).id,
                                localSourceHash = checkpoint.localSourceHash,
                                localSourceMessageCount = checkpoint.conversation.messageCount,
                                localSourceLastMessageDate =
                                    checkpoint.conversation.lastMessageDate,
                                localSourceMaxSmsId =
                                    checkpoint.conversation.messages.maxOfOrNull { it.id } ?: 0L,
                                localSourceDeviceId = deviceProfile.deviceId
                            )
                        }
                    )
                    check(updated == localCheckpointWrites.size) {
                        "Local checkpoint bootstrap transaction did not update every eligible row."
                    }
                }
                currentCoroutineContext().ensureActive()
                onCheckpointSummary(checkpointClassification.locallyUnchanged.size)
                onCheckpointDetails(checkpointClassification)
                Log.i(
                    "OpenSMSBackup",
                    "gmail_checkpoint_classification total=${conversations.size} " +
                        "matching=${checkpointClassification.locallyUnchanged.size} " +
                        "uninitialized=${checkpointClassification.uninitialized} " +
                        "device_mismatch=${checkpointClassification.deviceMismatched} " +
                        "hash_mismatch=${checkpointClassification.hashMismatched} " +
                        "missing_cached_id=${checkpointClassification.missingCachedGmailId} " +
                        "upgraded_legacy=${checkpointClassification.requiringLocalHashUpgrade.size} " +
                        "bootstrapped=${checkpointClassification.locallyBootstrappable.size}"
                )
                Log.i(
                    "OpenSMSBackup",
                    "gmail_legacy_bootstrap " +
                        "eligible=${checkpointClassification.locallyBootstrappable.size} " +
                        "initialized=${checkpointClassification.locallyBootstrappable.size} " +
                        "rejected_hash=${checkpointClassification.bootstrapRejectedHash} " +
                        "rejected_missing_id=${checkpointClassification.bootstrapRejectedMissingId} " +
                        "rejected_no_full_proof=${checkpointClassification.bootstrapRejectedNoFullProof} " +
                        "rejected_device=${checkpointClassification.bootstrapRejectedDevice} " +
                        "duration_ms=${android.os.SystemClock.elapsedRealtime() - bootstrapStarted}"
                )

                if (
                    ArchiveLocalCheckpointClassifier.isFastPathEligible(
                        runPlan.scope,
                        backupMode
                    ) &&
                    conversationsToCompare.isEmpty()
                ) {
                    onProgress(conversations.size, conversations.size, 0, conversations.size, 0)
                    onStage(GmailBackupStage.COMPLETING)
                    val duration =
                        android.os.SystemClock.elapsedRealtime() - runStartedElapsed
                    Log.i(
                        "OpenSMSBackup",
                        "gmail_backup_diagnostics sms_read_ms=$smsReadDuration " +
                            "conversation_build_ms=$conversationBuildDuration " +
                            "checkpoint_classification_ms=$classificationDuration " +
                            "index_built=false snapshot_reads=0 metadata_reads=0 uploads=0 " +
                            "locally_unchanged=${conversations.size} remotely_compared=0 " +
                            "remotely_unchanged=0 remote_recoveries=0 failed=0 duration_ms=$duration"
                    )
                    return@withContext Result.success(
                        GmailBackupCompletion(
                            state = GmailBackupCompletionState.COMPLETED,
                            checked = conversations.size,
                            total = conversations.size,
                            uploaded = 0,
                            unchanged = conversations.size,
                            failed = 0,
                            locallyUnchanged = conversations.size,
                            legacyLocallyInitialized =
                                checkpointClassification.locallyBootstrappable.size,
                            remotelyCompared = 0,
                            remotelyUnchanged = 0,
                            remoteRecoveries = 0,
                            profileId = accountProfile.profileId,
                            accountEmail = trimmedEmail,
                            totalMessages = includedMessageTotal,
                            sourceConversationTotal = runPlan.sourceConversationCount,
                            sourceMessageTotal = messages.size,
                            durationMillis = duration,
                            backupScope = runPlan.scope,
                            backupMode = backupMode,
                            gmailIndexUsed = false
                        )
                    )
                }

                if (runPlan.isLimitedTest) {
                    Log.i(
                        "OpenSMSBackup",
                        "TEST MODE: Selected ${conversations.size} most recently active conversations " +
                            "from ${runPlan.sourceConversationCount} local conversations."
                    )
                    conversations.forEachIndexed { index, conversation ->
                        val canonical = SmsAddressNormalizer()
                            .normalize(conversation.address, deviceProfile.defaultRegion).canonical
                        Log.d(
                            "OpenSMSBackup",
                            "test_scope_position=${index + 1} " +
                                "identity=${GmailBackupConversationLimiter.safeIdentity(canonical)} " +
                                "messages=${conversation.messageCount}"
                        )
                    }
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

                val fullSnapshotReads = AtomicInteger()
                val metadataReads = AtomicInteger()
                val remoteRecoveries = AtomicInteger()
                val archiveReader = GmailArchivedConversationReader(
                    gmail = gmailService,
                    profileId = accountProfile.profileId,
                    onRetry = onRetry,
                    onFullSnapshotRead = { fullSnapshotReads.incrementAndGet() },
                    onMetadataRead = { metadataReads.incrementAndGet() },
                    onTiming = { operation, durationMillis, success ->
                        Log.i(
                            "OpenSMSBackup",
                            "gmail_remote_timing operation=$operation duration_ms=$durationMillis " +
                                "success=$success"
                        )
                    }
                )
                val archiveIndex =
                    if (
                        runPlan.scope == GmailBackupScope.FULL &&
                        backupMode == GmailBackupMode.ARCHIVE_APPEND_ONLY
                    ) {
                        onStage(GmailBackupStage.RECOVERING_GMAIL_INDEX)
                        val indexStarted = android.os.SystemClock.elapsedRealtime()
                        onIndexProgress(0, 0)
                        GmailArchiveIndex.build(
                            source = archiveReader,
                            accountEmail = trimmedEmail,
                            deviceId = deviceProfile.deviceId,
                            deviceLabelId = deviceLabelId,
                            onProgress = onIndexProgress
                        ).also {
                            Log.i(
                                "OpenSMSBackup",
                                "gmail_index built=true duration_ms=${android.os.SystemClock.elapsedRealtime() - indexStarted}"
                            )
                        }
                    } else {
                        null
                    }
                val lazyArchiveIndex =
                    if (
                        archiveIndex == null &&
                        backupMode == GmailBackupMode.ARCHIVE_APPEND_ONLY
                    ) {
                        LazyGmailArchiveIndex {
                            onStage(GmailBackupStage.RECOVERING_GMAIL_INDEX)
                            val indexStarted = android.os.SystemClock.elapsedRealtime()
                            onIndexProgress(0, 0)
                            GmailArchiveIndex.build(
                                source = archiveReader,
                                accountEmail = trimmedEmail,
                                deviceId = deviceProfile.deviceId,
                                deviceLabelId = deviceLabelId,
                                onProgress = onIndexProgress
                            ).also {
                                Log.i(
                                    "OpenSMSBackup",
                                    "gmail_index built=true duration_ms=${android.os.SystemClock.elapsedRealtime() - indexStarted}"
                                )
                            }
                        }
                    } else {
                        null
                    }

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
                            defaultRegion = deviceProfile.defaultRegion,
                            index = archiveIndex,
                            indexProvider = lazyArchiveIndex?.let { lazy -> { lazy.get() } },
                            onRemoteRecovery = {
                                onRemoteRecovery(remoteRecoveries.incrementAndGet())
                            }
                        ),
                        uploader = uploader,
                        snapshotDao = snapshotDao,
                        accountId = accountId,
                        accountEmail = trimmedEmail,
                        deviceProfile = deviceProfile,
                        onInserted = { conversation, result, internalDate ->
                            (archiveIndex ?: lazyArchiveIndex?.peek())?.recordUploaded(
                                conversation = conversation,
                                accountEmail = trimmedEmail,
                                deviceId = deviceProfile.deviceId,
                                defaultRegion = deviceProfile.defaultRegion,
                                messageId = result.messageId,
                                threadId = result.threadId,
                                internalDate = internalDate
                            )
                        },
                        onUploading = {
                            onStage(GmailBackupStage.UPLOADING_CHANGED_CONVERSATIONS)
                        },
                        onTiming = { operation, durationMillis, success ->
                            Log.i(
                                "OpenSMSBackup",
                                "gmail_remote_timing operation=$operation duration_ms=$durationMillis " +
                                    "success=$success"
                            )
                        }
                    )
                val backupStrategy = BackupStrategySelector(
                    mirrorStrategy = mirrorStrategy,
                    archiveAppendStrategy = archiveAppendStrategy
                ).select(backupMode)

                val classifier = GmailErrorClassifier()
                val circuitBreaker = GmailFailureCircuitBreaker()
                var abortFailure: io.github.isht1008.opensmsbackup.gmail.error.GmailFailure? = null
                var abortState: GmailBackupCompletionState? = null

                var checked = checkpointClassification.locallyUnchanged.size
                var uploaded = 0
                var skipped = checkpointClassification.locallyUnchanged.size
                var remotelyUnchanged = 0
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

                for (conversation in conversationsToCompare) {

                    currentCoroutineContext()
                        .ensureActive()
                    yield()
                    onStage(GmailBackupStage.COMPARING_CHANGED_CONVERSATIONS)

                    val snapshotHash =
                        ConversationSnapshotHashGenerator
                            .generate(
                                conversation
                            )

                    val existingSnapshot = snapshotsByThreadId[conversation.threadId]

                    if (backupMode == GmailBackupMode.MIRROR && runPlan.isLimitedTest) {
                        val canonical = SmsAddressNormalizer()
                            .normalize(conversation.address, deviceProfile.defaultRegion).canonical
                        Log.d(
                            "OpenSMSBackup",
                            "mirror_test_check identity=${GmailBackupConversationLimiter.safeIdentity(canonical)} " +
                                "messages=${conversation.messageCount} " +
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

                        if (runPlan.isLimitedTest) {
                            Log.d("OpenSMSBackup", "mirror_test_result unchanged=true uploaded=false")
                        }

                        continue
                    }

                    val email = if (backupMode == GmailBackupMode.MIRROR) {
                        mimeMessageBuilder.build(
                            conversation = conversation,
                            accountEmail = trimmedEmail,
                            snapshotHash = snapshotHash,
                            deviceProfile = deviceProfile
                        )
                    } else {
                        null
                    }

                    val uploadResult = try {
                        backupStrategy.execute(
                            conversation = conversation,
                            email = email,
                            snapshotHash = snapshotHash,
                            localSourceHash = localHashesByThreadId[conversation.threadId],
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
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }

                    uploadResult
                        .onSuccess { gmailResult ->
                            if (gmailResult.wasUploaded) {
                                uploaded++
                            } else {
                                skipped++
                                remotelyUnchanged++
                            }
                            if (backupMode == GmailBackupMode.MIRROR && runPlan.isLimitedTest) {
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
                val duration =
                    android.os.SystemClock.elapsedRealtime() - runStartedElapsed
                onStage(GmailBackupStage.COMPLETING)
                Log.i(
                    "OpenSMSBackup",
                    "gmail_backup_diagnostics sms_read_ms=$smsReadDuration " +
                        "conversation_build_ms=$conversationBuildDuration " +
                        "checkpoint_classification_ms=$classificationDuration " +
                        "index_built=${archiveIndex != null || lazyArchiveIndex?.wasBuilt == true} " +
                        "snapshot_reads=${fullSnapshotReads.get()} metadata_reads=${metadataReads.get()} " +
                        "uploads=$uploaded locally_unchanged=${checkpointClassification.locallyUnchanged.size} " +
                        "remotely_compared=${(checked - checkpointClassification.locallyUnchanged.size).coerceAtLeast(0)} " +
                        "remotely_unchanged=$remotelyUnchanged remote_recoveries=${remoteRecoveries.get()} " +
                        "failed=$failed duration_ms=$duration"
                )

                advanceFullBackupMetadataIfEligible(
                    scope = runPlan.scope,
                    abortState = abortState,
                    failed = failed
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
                        state = GmailBackupRunPlanner.completionState(
                            scope = runPlan.scope,
                            abortState = abortState,
                            failed = failed
                        ),
                        checked = checked,
                        total = conversations.size,
                        uploaded = uploaded,
                        unchanged = skipped,
                        failed = failed,
                        locallyUnchanged = checkpointClassification.locallyUnchanged.size,
                        legacyLocallyInitialized =
                            checkpointClassification.locallyBootstrappable.size,
                        remotelyCompared =
                            (checked - checkpointClassification.locallyUnchanged.size)
                                .coerceAtLeast(0),
                        remotelyUnchanged = remotelyUnchanged,
                        remoteRecoveries = remoteRecoveries.get(),
                        previousSnapshotsTrashed = previousSnapshotsTrashed,
                        reason = abortFailure?.userMessage ?: if (runPlan.isLimitedTest) {
                            buildString {
                                if (backupMode == GmailBackupMode.MIRROR) {
                                    append("Mirror test completed for the 10 most recently active conversations: ")
                                } else {
                                    append("Archive test completed for the 10 most recently active conversations: ")
                                }
                                append("$checked checked, $uploaded replaced/uploaded, $skipped unchanged, ")
                                append("$previousSnapshotsTrashed previous snapshots moved to Trash, $failed failed.")
                            }
                        } else if (failed > 0) {
                            "Completed with failures: $checked checked, $uploaded uploaded, " +
                                "$skipped unchanged, $failed failed, " +
                                "${(conversations.size - checked).coerceAtLeast(0)} remaining."
                        } else {
                            null
                        },
                        profileId = accountProfile.profileId,
                        accountEmail = trimmedEmail,
                        failure = abortFailure,
                        totalMessages = includedMessageTotal,
                        stoppedAtSafetyLimit = runPlan.isLimitedTest,
                        isLimitedTest = runPlan.isLimitedTest,
                        sourceConversationTotal = runPlan.sourceConversationCount,
                        sourceMessageTotal = messages.size,
                        durationMillis = duration,
                        backupScope = runPlan.scope,
                        backupMode = backupMode,
                        gmailIndexUsed = archiveIndex != null || lazyArchiveIndex?.wasBuilt == true,
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
