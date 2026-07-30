package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import android.util.Log
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationDao
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.ArchiveMessageNotFoundException
import io.github.isht1008.opensmsbackup.gmail.backup.DeviceSnapshotOwnership
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchivedConversationReader
import io.github.isht1008.opensmsbackup.gmail.backup.LocalConversationSourceHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshotBuilder
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabelManager
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploadResult
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploader
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.CancellationException
import java.util.Locale

class FullMirrorExecutionService(private val context: Context) {
    suspend fun execute(
        runId: String,
        preflightOnly: Boolean = false,
        onProgress: suspend (FullMirrorExecutionSummary) -> Unit = {}
    ): Result<FullMirrorExecutionSummary> = runCatching {
        val database = DatabaseProvider.getDatabase(context)
        val dao = database.mirrorReconciliationDao()
        val run = requireNotNull(dao.findRun(runId)) { "Mirror plan does not exist." }
        require(run.status in setOf(FullMirrorRunStatus.CONFIRMED.name, FullMirrorRunStatus.CANCELLED.name, FullMirrorRunStatus.RUNNING.name, FullMirrorRunStatus.COMPLETED_WITH_WARNINGS.name))
        val profile = requireNotNull(database.accountProfileDao().findProfileById(run.profileId))
        val settings = requireNotNull(database.accountProfileDao().findSettings(run.profileId))
        val device = DeviceProfileStore.create(context).getOrCreate()
        require(profile.accountEmail.trim().lowercase(Locale.ROOT) == run.accountIdentity)
        require(settings.backupMode == GmailBackupMode.MIRROR.name)
        require(device.deviceId == run.deviceId)
        require(DeviceProfileStore.create(context).getGmailDeviceLabelId(run.profileId) == run.deviceLabelId)
        val entities = dao.findItems(runId)
        val incomplete = entities.filter { it.state !in setOf(FullMirrorItemState.COMPLETED.name, FullMirrorItemState.SKIPPED_CONFLICT.name) }
        val cleanupOnly = incomplete.isNotEmpty() && incomplete.all {
            it.action == FullMirrorAction.REPLACE_CHANGED.name &&
                it.state in setOf(FullMirrorItemState.PERSISTED.name, FullMirrorItemState.TRASH_PENDING.name, FullMirrorItemState.WARNING.name) &&
                !it.resultingGmailMessageId.isNullOrBlank()
        }
        require(!preflightOnly || cleanupOnly) { "Read-only preflight is limited to deterministic cleanup-only recovery." }
        val conversations = if (cleanupOnly) {
            emptyList()
        } else {
            val sms = SmsRepository().getCompleteSmsMessages(context, settings.includeContactNames)
            require(sms.complete && sms.providerCount == sms.messages.size) { "Local SMS scan is incomplete." }
            SmsConversationSnapshotBuilder().build(sms.messages).also {
                require(FullMirrorPreviewPlanner.fingerprintLocal(it) == run.localDatasetFingerprint) {
                    "Local SMS changed after preview. Create a new preview."
                }
            }
        }
        val byThread = conversations.associateBy { it.threadId }
        val preview = run.toPreview(entities.map { it.toPreviewItem() })
        require(System.currentTimeMillis() <= run.expiresAt || run.status in setOf(FullMirrorRunStatus.CANCELLED.name, FullMirrorRunStatus.COMPLETED_WITH_WARNINGS.name)) {
            "Mirror preview expired."
        }
        val gmail = GmailApiClient(context).createService(profile)
        val reader = GmailArchivedConversationReader(gmail, profile.profileId)
        val snapshotDao = database.conversationSnapshotDao()
        val cache = snapshotDao.findAllForProfile(run.profileId, run.accountIdentity).associateBy { it.androidThreadId }
        val expectedResultById = if (cleanupOnly) {
            entities.mapNotNull { entity ->
                entity.resultingGmailMessageId?.let { it to entity }
            }.toMap()
        } else emptyMap()
        val currentRemote = mutableListOf<OwnedRemoteSnapshot>()
        var unexplainedScoped = 0
        var remotePageToken: String? = null
        do {
            val page = reader.listPage(run.deviceLabelId, remotePageToken)
            val metadata = reader.readMetadataPage(page.messageIds)
            for (reference in metadata) {
                val scoped = reference.accountHeader.equals(run.accountIdentity, true) &&
                    reference.deviceIdHeader == run.deviceId && run.deviceLabelId in reference.labelIds
                if (!scoped) continue
                if (reference.formatVersionHeader != "3" ||
                    reference.identityVersionHeader !in setOf("2", "3", MirrorConversationIdentity.WIRE_NAME) ||
                    reference.conversationKeyHeader.isNullOrBlank()
                ) {
                    unexplainedScoped++
                    continue
                }
                if (cleanupOnly) {
                    val resultEntity = expectedResultById[reference.messageId]
                    val resultThread = resultEntity?.androidThreadId
                    val resultOwned = resultEntity == null ||
                        reference.identityVersionHeader == MirrorConversationIdentity.WIRE_NAME &&
                        reference.conversationKeyHeader == resultEntity.conversationKey &&
                        resultThread != null && resultThread > 0L
                    currentRemote += OwnedRemoteSnapshot(
                        reference.conversationKeyHeader.orEmpty(),
                        reference.messageId,
                        resultThread?.let { cache[it]?.snapshotHash },
                        resultThread,
                        resultOwned,
                        true,
                        cacheMatches = resultThread?.let { cache[it]?.gmailMessageId } == reference.messageId,
                        identityCurrent = reference.identityVersionHeader == MirrorConversationIdentity.WIRE_NAME,
                        reason = if (resultOwned) FullMirrorFailureCategory.NONE else FullMirrorFailureCategory.OWNERSHIP,
                        ownershipConversationKeyHeader = reference.conversationKeyHeader,
                        identityVersionHeader = reference.identityVersionHeader,
                        formatVersionHeader = reference.formatVersionHeader
                    )
                    continue
                }
                val document = reader.read(reference.messageId).getOrNull()
                currentRemote += if (document == null) {
                    OwnedRemoteSnapshot(reference.conversationKeyHeader.orEmpty(), reference.messageId, null, null, false, false, reason = FullMirrorFailureCategory.UNREADABLE)
                } else {
                    val threadId = document.conversation.threadId
                    val mirrorKey = if (threadId > 0L) MirrorConversationIdentity.key(run.profileId, run.accountIdentity, run.deviceId, threadId)
                        else reference.conversationKeyHeader.orEmpty()
                    val cached = cache[threadId]
                    val cacheMatches = cached?.profileId == run.profileId && cached.accountId == run.accountIdentity && cached.gmailMessageId == reference.messageId
                    val currentIdentity = reference.identityVersionHeader == MirrorConversationIdentity.WIRE_NAME
                    val currentOwned = currentIdentity && DeviceSnapshotOwnership.matchesMirrorThread(
                        document, run.profileId, run.accountIdentity, run.deviceId, run.deviceLabelId, threadId
                    )
                    val legacyOwned = !currentIdentity && document.conversation.threadId == threadId && DeviceSnapshotOwnership.matchesV2(
                        document, run.accountIdentity, run.deviceId, run.deviceLabelId, document.conversation, device.defaultRegion
                    )
                    OwnedRemoteSnapshot(
                        mirrorKey, reference.messageId, ConversationSnapshotHashGenerator.generate(document.conversation), threadId,
                        currentOwned || legacyOwned, true, cacheMatches = cacheMatches, identityCurrent = currentIdentity,
                        reason = if (!currentOwned && !legacyOwned) FullMirrorFailureCategory.OWNERSHIP else FullMirrorFailureCategory.NONE,
                        ownershipConversationKeyHeader = reference.conversationKeyHeader,
                        identityVersionHeader = reference.identityVersionHeader,
                        formatVersionHeader = reference.formatVersionHeader
                    )
                }
            }
            require(currentRemote.size <= 100_000) { "Mirror remote revalidation exceeded its safety limit." }
            remotePageToken = page.nextPageToken
        } while (remotePageToken != null)
        if (!cleanupOnly) {
            val fingerprintMatches = FullMirrorPreviewPlanner.fingerprintRemote(currentRemote) == run.remoteIndexFingerprint
            Log.i("OpenSMSBackup", "full_mirror_remote_validation mode=initial fingerprint_match=$fingerprintMatches")
            require(fingerprintMatches) {
                "Mirror remote state changed after preview. Create a new preview."
            }
        } else {
            val resultingIds = entities.mapNotNull { it.resultingGmailMessageId }
            val trashedOldIds = entities.filter {
                it.state == FullMirrorItemState.COMPLETED.name &&
                    it.action in setOf(FullMirrorAction.REPLACE_CHANGED.name, FullMirrorAction.TRASH_REMOTE_ONLY.name)
            }.mapNotNull { it.priorGmailMessageId }.toSet()
            val originalIds = entities.mapNotNull { it.priorGmailMessageId }.toSet()
            val actualIds = currentRemote.map { it.messageId }
            val reconciliation = FullMirrorResumeReconciliation.evaluate(
                originalIds, resultingIds, trashedOldIds, actualIds,
                unexplainedScoped + currentRemote.count { !it.readable || !it.ownershipValid }
            )
            Log.i(
                "OpenSMSBackup",
                "full_mirror_remote_validation mode=resume allowed=${reconciliation.allowed} attributable_additions=${reconciliation.attributableAdditions} attributable_trash=${reconciliation.attributableTrash} unexplained_additions=${reconciliation.unexplainedAdditions} unexplained_removals=${reconciliation.unexplainedRemovals} duplicates=${reconciliation.duplicates} scoped_errors=${reconciliation.scopedErrors}"
            )
            require(reconciliation.allowed) { "Unexplained remote change blocks resume." }
            val remoteById = currentRemote.associateBy { it.messageId }
            val resultEntities = entities.filter { it.resultingGmailMessageId != null }
            val identityMismatches = resultEntities.count { entity ->
                remoteById[entity.resultingGmailMessageId]?.identityCurrent != true
            }
            val threadMismatches = resultEntities.count { entity ->
                remoteById[entity.resultingGmailMessageId]?.androidThreadId != entity.androidThreadId
            }
            val keyMismatches = resultEntities.count { entity ->
                remoteById[entity.resultingGmailMessageId]?.conversationKey != entity.conversationKey
            }
            val pointerMismatches = resultEntities.count { entity ->
                val threadId = entity.androidThreadId
                threadId == null || cache[threadId]?.gmailMessageId != entity.resultingGmailMessageId
            }
            Log.i(
                "OpenSMSBackup",
                "full_mirror_persisted_headers expected=${resultEntities.size} identity_mismatch=$identityMismatches thread_mismatch=$threadMismatches key_mismatch=$keyMismatches room_pointer_mismatch=$pointerMismatches"
            )
            require(identityMismatches == 0 && threadMismatches == 0 &&
                keyMismatches == 0 && pointerMismatches == 0
            ) { "Persisted replacement/upload header validation failed." }
            resultEntities.forEach { entity ->
                val resultId = requireNotNull(entity.resultingGmailMessageId)
                requireNotNull(remoteById[resultId]) { "Expected persisted upload is missing." }
            }
        }
        val uploader = GmailUploader(
            gmail = gmail,
            profileId = profile.profileId,
            labelManager = GmailLabelManager(
                gmail = gmail,
                profileId = profile.profileId,
                deviceProfile = device,
                cachedDeviceLabelId = run.deviceLabelId,
                onDeviceLabelResolved = { resolved -> require(resolved == run.deviceLabelId) }
            )
        )
        val uploadResults = mutableMapOf<String, GmailUploadResult>()
       val gateway = object : FullMirrorMutationGateway {
            override suspend fun validateBinding(binding: FullMirrorBinding): Boolean {
                val currentProfile = database.accountProfileDao().findProfileById(binding.profileId) ?: return false
                val currentSettings = database.accountProfileDao().findSettings(binding.profileId) ?: return false
                val currentDevice = DeviceProfileStore.create(context).getOrCreate()
                return currentProfile.accountEmail.trim().lowercase(Locale.ROOT) == binding.accountIdentity &&
                    currentSettings.backupMode == GmailBackupMode.MIRROR.name &&
                    currentDevice.deviceId == binding.deviceId
            }
            override suspend fun currentLocalSourceHash(item: FullMirrorPreviewItem): String? =
                item.androidThreadId?.let(byThread::get)?.let(LocalConversationSourceHashGenerator::generate)

            override suspend fun inspectOldTarget(
                binding: FullMirrorBinding,
                item: FullMirrorPreviewItem
            ): FullMirrorOldTargetStatus {
                val proof = item.oldTargetProof ?: return FullMirrorOldTargetStatus.INVALID
                if (!proof.isCompleteFor(binding, item)) return FullMirrorOldTargetStatus.INVALID
                val read = reader.read(proof.gmailMessageId)
                val document = read.getOrNull() ?: return when (read.exceptionOrNull()) {
                    is ArchiveMessageNotFoundException -> FullMirrorOldTargetStatus.MISSING
                    else -> FullMirrorOldTargetStatus.AMBIGUOUS
                }
                return FullMirrorOldTargetValidator.inspect(binding, item, document, device.defaultRegion)
            }

            override suspend fun validatePersistedReplacement(
                binding: FullMirrorBinding,
                item: FullMirrorPreviewItem,
                resultingMessageId: String
            ): Boolean {
                val threadId = item.androidThreadId ?: return false
                if (resultingMessageId == item.priorGmailMessageId) return false
                val cached = snapshotDao.findForProfile(binding.profileId, binding.accountIdentity, threadId)
                    ?: return false
                if (cached.gmailMessageId != resultingMessageId ||
                    cached.localSourceHash != item.expectedLocalSourceHash ||
                    cached.localSourceDeviceId != binding.deviceId
                ) return false
                val document = reader.read(resultingMessageId).getOrNull() ?: return false
                return DeviceSnapshotOwnership.matchesMirrorThread(
                    document, binding.profileId, binding.accountIdentity, binding.deviceId,
                    binding.deviceLabelId, threadId
                ) && ConversationSnapshotHashGenerator.generate(document.conversation) == cached.snapshotHash
            }
            override suspend fun upload(item: FullMirrorPreviewItem): String {
                val conversation = requireConversation(item, byThread)
                val snapshotHash = ConversationSnapshotHashGenerator.generate(conversation)
                val email = ConversationMimeMessageBuilder().build(
                    conversation, run.accountIdentity, snapshotHash, device, item.conversationKey, MirrorConversationIdentity.WIRE_NAME
                )
                val result = uploader.uploadConversation(email).getOrThrow()
                uploadResults[item.itemId] = result
                return result.messageId
            }
            override suspend fun persistUploaded(item: FullMirrorPreviewItem, resultingMessageId: String) {
                val conversation = requireConversation(item, byThread)
                val result = requireNotNull(uploadResults[item.itemId])
                val existing = snapshotDao.findForProfile(run.profileId, run.accountIdentity, conversation.threadId)
                snapshotDao.insert(snapshot(conversation, result, existing?.id ?: 0L))
            }
            override suspend fun trashOwned(messageId: String) { uploader.trashMessage(messageId).getOrThrow() }
            override suspend fun persistRemoteOnlyRemoval(item: FullMirrorPreviewItem) {
                val threadId = requireNotNull(item.androidThreadId)
                val messageId = requireNotNull(item.priorGmailMessageId)
                snapshotDao.deleteOwnedSnapshot(run.profileId, run.accountIdentity, threadId, messageId)
            }
            override suspend fun recoverCache(item: FullMirrorPreviewItem) {
                val messageId = requireNotNull(item.priorGmailMessageId)
                val document = reader.read(messageId).getOrThrow()
                val result = GmailUploadResult(document.messageId, document.threadId, document.labelIds.toList(), false)
                val existing = snapshotDao.findForProfile(run.profileId, run.accountIdentity, document.conversation.threadId)
                snapshotDao.insert(snapshot(document.conversation, result, existing?.id ?: 0L))
            }
            private fun snapshot(conversation: SmsConversationSnapshot, result: GmailUploadResult, id: Long) = ConversationSnapshotEntity(
                id = id,
                profileId = run.profileId,
                accountId = run.accountIdentity,
                accountEmail = profile.accountEmail,
                androidThreadId = conversation.threadId,
                address = conversation.address.orEmpty(),
                contactName = conversation.contactName,
                messageCount = conversation.messageCount,
                snapshotHash = ConversationSnapshotHashGenerator.generate(conversation),
                localSourceHash = LocalConversationSourceHashGenerator.generate(conversation),
                localSourceMessageCount = conversation.messageCount,
                localSourceLastMessageDate = conversation.lastMessageDate,
                localSourceMaxSmsId = conversation.messages.maxOfOrNull { it.id } ?: 0L,
                localSourceDeviceId = run.deviceId,
                gmailMessageId = result.messageId,
                gmailThreadId = result.threadId,
                firstMessageDate = conversation.firstMessageDate,
                lastMessageDate = conversation.lastMessageDate
            )
        }
        val journal = object : FullMirrorExecutionJournal {
            override suspend fun state(itemId: String) = FullMirrorItemState.valueOf(requireNotNull(dao.findItem(runId, itemId)).state)
            override suspend fun mark(itemId: String, state: FullMirrorItemState, resultingMessageId: String?, warning: FullMirrorFailureCategory?) {
                check(dao.updateItem(runId, itemId, run.profileId, state.name, if (state == FullMirrorItemState.VALIDATING) 1 else 0, resultingMessageId, warning?.name, null, if (state == FullMirrorItemState.COMPLETED) System.currentTimeMillis() else null) == 1)
            }
        }
        if (cleanupOnly) {
            val cleanupItems = preview.items.filter { item ->
                val entity = entities.first { it.itemId == item.itemId }
                entity.state !in setOf(FullMirrorItemState.COMPLETED.name, FullMirrorItemState.SKIPPED_CONFLICT.name)
            }
            require(cleanupItems.size == incomplete.size)
            for (item in cleanupItems) {
                val resultingId = requireNotNull(item.resultingGmailMessageId)
                require(gateway.validatePersistedReplacement(preview.binding, item, resultingId)) {
                    "Persisted replacement proof blocks cleanup resume."
                }
                require(gateway.inspectOldTarget(preview.binding, item) in setOf(
                    FullMirrorOldTargetStatus.PRESENT_VALID,
                    FullMirrorOldTargetStatus.ALREADY_TRASHED_VALID
                )) { "Old-target ownership proof blocks cleanup resume." }
            }
            val completedCleanup = preview.items.filter { item ->
                val entity = entities.first { it.itemId == item.itemId }
                entity.state == FullMirrorItemState.COMPLETED.name &&
                    item.action in setOf(FullMirrorAction.REPLACE_CHANGED, FullMirrorAction.TRASH_REMOTE_ONLY)
            }
            for (item in completedCleanup) {
                require(gateway.inspectOldTarget(preview.binding, item) == FullMirrorOldTargetStatus.ALREADY_TRASHED_VALID) {
                    "Completed Trash attribution cannot be revalidated."
                }
            }
            Log.i(
                "OpenSMSBackup",
                "full_mirror_cleanup_preflight allowed=true remaining=${cleanupItems.size} uploads=0 old_targets_validated=${cleanupItems.size}"
            )
        }
        if (preflightOnly) {
            return@runCatching FullMirrorExecutionSummary(
                completed = entities.count { it.state == FullMirrorItemState.COMPLETED.name },
                warnings = entities.count { it.state == FullMirrorItemState.WARNING.name },
                failed = entities.count { it.state == FullMirrorItemState.FAILED.name },
                remaining = incomplete.size,
                newUploaded = 0,
                changedReplaced = 0,
                previousTrashed = 0,
                remoteOnlyTrashed = 0
            )
        }
        dao.updateRunStatus(runId, run.profileId, FullMirrorRunStatus.RUNNING.name, null, null)
        try {
            val summary = FullMirrorExecutor(gateway, journal, onProgress).execute(preview)
            val status = if (summary.failed > 0) FullMirrorRunStatus.FAILED else if (summary.warnings > 0) FullMirrorRunStatus.COMPLETED_WITH_WARNINGS else FullMirrorRunStatus.COMPLETED
            dao.updateRunStatus(runId, run.profileId, status.name, System.currentTimeMillis(), null)
            summary
        } catch (cancel: CancellationException) {
            dao.updateRunStatus(runId, run.profileId, FullMirrorRunStatus.CANCELLED.name, null, FullMirrorFailureCategory.CANCELLED.name)
            throw cancel
        } catch (error: Throwable) {
            dao.updateRunStatus(runId, run.profileId, FullMirrorRunStatus.FAILED.name, System.currentTimeMillis(), "SAFETY_VALIDATION")
            throw error
        }
    }

    private fun requireConversation(item: FullMirrorPreviewItem, map: Map<Long, SmsConversationSnapshot>) =
        requireNotNull(item.androidThreadId?.let(map::get)) { "Local conversation no longer exists." }

    private fun io.github.isht1008.opensmsbackup.database.MirrorReconciliationRunEntity.toPreview(items: List<FullMirrorPreviewItem>) = FullMirrorPreview(
        binding = FullMirrorBinding(runId, profileId, accountIdentity, deviceId, deviceLabelId, expectedPolicy),
        createdAt = createdAt, expiresAt = expiresAt, localDatasetFingerprint = localDatasetFingerprint,
        remoteIndexFingerprint = remoteIndexFingerprint, localConversations = localConversations,
        sourceMessages = 0, ownedRemoteConversations = ownedRemoteConversations,
        foreignIgnored = foreignIgnoredCount, items = items, localScanComplete = localScanComplete,
        blockedReason = terminalReason?.let { runCatching { FullMirrorFailureCategory.valueOf(it) }.getOrNull() }
    )
    private fun io.github.isht1008.opensmsbackup.database.MirrorReconciliationItemEntity.toPreviewItem() = FullMirrorPreviewItem(
        itemId, conversationKey, androidThreadId, FullMirrorAction.valueOf(action), expectedLocalSourceHash,
        expectedRemoteSnapshotHash, priorGmailMessageId,
        failureCategory?.let { runCatching { FullMirrorFailureCategory.valueOf(it) }.getOrNull() } ?: FullMirrorFailureCategory.NONE,
        oldTargetProof = if (oldTargetProfileId != null && oldTargetAccountIdentity != null &&
            oldTargetDeviceId != null && oldTargetDeviceLabelId != null &&
            oldTargetAndroidThreadId != null && oldTargetGmailMessageId != null &&
            oldTargetSnapshotHash != null && oldTargetProofVersion != null
        ) FullMirrorOldTargetProof(
            oldTargetProfileId, oldTargetAccountIdentity, oldTargetDeviceId, oldTargetDeviceLabelId,
            oldTargetAndroidThreadId, oldTargetGmailMessageId, oldTargetSnapshotHash,
            oldTargetConversationKeyHeader, oldTargetIdentityVersionHeader,
            oldTargetFormatVersionHeader, oldTargetProofVersion
        ) else null,
        resultingGmailMessageId = resultingGmailMessageId
    )
}
