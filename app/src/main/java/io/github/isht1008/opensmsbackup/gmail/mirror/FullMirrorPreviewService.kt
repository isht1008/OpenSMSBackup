package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import android.util.Log
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.BackupDatabase
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationDao
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationItemEntity
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationRunEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.DeviceSnapshotOwnership
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchivedConversationReader
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshotBuilder
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale
import java.util.UUID

class FullMirrorPreviewService(private val context: Context) {
    suspend fun createPreview(
        profile: AccountProfileEntity,
        includeContactNames: Boolean,
        now: Long = System.currentTimeMillis()
    ): Result<FullMirrorPreview> = runCatching {
        currentCoroutineContext().ensureActive()
        require(profile.connectionState == AccountProfileEntity.CONNECTION_STATE_CONNECTED) { "Mirror account is not connected." }
        val repository = MultiAccountRepository.create(context)
        require(repository.getBackupMode(profile.profileId) == GmailBackupMode.MIRROR) { "The immutable profile policy is no longer Mirror." }
        val deviceStore = DeviceProfileStore.create(context)
        val device = deviceStore.getOrCreate()
        val deviceLabelId = requireNotNull(deviceStore.getGmailDeviceLabelId(profile.profileId)) {
            "No existing device-scoped Gmail label is bound to this Mirror profile. Run the limited Mirror test first."
        }
        val account = profile.accountEmail.trim().lowercase(Locale.ROOT)
        val binding = FullMirrorBinding(UUID.randomUUID().toString(), profile.profileId, account, device.deviceId, deviceLabelId, GmailBackupMode.MIRROR.name)
        val smsRead = SmsRepository().getCompleteSmsMessages(context, includeContactNames)
        currentCoroutineContext().ensureActive()
        val local = CompleteLocalSmsDataset(
            SmsConversationSnapshotBuilder().build(smsRead.messages), smsRead.messages.size,
            smsRead.complete, smsRead.providerCount == smsRead.messages.size,
            if (smsRead.failureCategory == null) FullMirrorFailureCategory.NONE else FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN
        )
        val database = io.github.isht1008.opensmsbackup.database.DatabaseProvider.getDatabase(context)
        require(database.mirrorReconciliationDao().activeCount(profile.profileId, device.deviceId) == 0) {
            "A confirmed or running Full Mirror plan requires reconciliation before a fresh preview."
        }
        val cache = database.conversationSnapshotDao().findAllForProfile(profile.profileId, account).associateBy { it.androidThreadId }
        val reader = GmailArchivedConversationReader(GmailApiClient(context).createService(profile), profile.profileId)
        val metadata = mutableListOf<io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveMetadataReference>()
        var pageToken: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val page = reader.listPage(deviceLabelId, pageToken)
            metadata += reader.readMetadataPage(page.messageIds)
            require(metadata.size <= 100_000) { "Mirror preview remote index exceeded the safety limit." }
            pageToken = page.nextPageToken
        } while (pageToken != null)

        var foreignIgnored = 0
        val discoveryReasons = mutableMapOf<FullMirrorFailureCategory, Int>()
        fun reject(reason: FullMirrorFailureCategory) {
            foreignIgnored++
            discoveryReasons[reason] = (discoveryReasons[reason] ?: 0) + 1
        }
        val accepted = metadata.filter { ref ->
            val reason = when {
                ref.messageId.isBlank() || ref.accountHeader.isNullOrBlank() || ref.deviceIdHeader.isNullOrBlank() || ref.conversationKeyHeader.isNullOrBlank() -> FullMirrorFailureCategory.MISSING_OWNERSHIP_HEADER
                deviceLabelId !in ref.labelIds -> FullMirrorFailureCategory.LABEL_MISMATCH
                !ref.accountHeader.equals(account, true) -> FullMirrorFailureCategory.ACCOUNT_MISMATCH
                ref.deviceIdHeader != device.deviceId -> FullMirrorFailureCategory.DEVICE_MISMATCH
                ref.formatVersionHeader != "3" -> FullMirrorFailureCategory.FORMAT_VERSION_MISMATCH
                ref.identityVersionHeader !in setOf("2", "3", MirrorConversationIdentity.WIRE_NAME) -> FullMirrorFailureCategory.UNSUPPORTED_IDENTITY_VERSION
                else -> null
            }
            if (reason != null) reject(reason)
            reason == null
        }
        val remotes = mutableListOf<OwnedRemoteSnapshot>()
        for (reference in accepted) {
            currentCoroutineContext().ensureActive()
            val document = reader.read(reference.messageId).getOrElse { error ->
                if (error is CancellationException) throw error
                remotes += OwnedRemoteSnapshot(reference.conversationKeyHeader.orEmpty(), reference.messageId, null, null, false, false, reason = FullMirrorFailureCategory.UNREADABLE)
                continue
            }
            val threadId = document.conversation.threadId
            val mirrorKey = if (threadId > 0L) MirrorConversationIdentity.key(profile.profileId, account, device.deviceId, threadId)
                else reference.conversationKeyHeader.orEmpty()
            val cached = cache[threadId]
            val cacheMatches = cached?.profileId == profile.profileId && cached.accountId == account && cached.gmailMessageId == reference.messageId
            val currentIdentity = reference.identityVersionHeader == MirrorConversationIdentity.WIRE_NAME
            val currentOwned = currentIdentity && DeviceSnapshotOwnership.matchesMirrorThread(
                document, profile.profileId, account, device.deviceId, deviceLabelId, threadId
            )
            val legacyOwned = !currentIdentity && cacheMatches && DeviceSnapshotOwnership.matchesV2(
                document, account, device.deviceId, deviceLabelId, document.conversation, device.defaultRegion
            )
            val reason = when {
                threadId <= 0L -> FullMirrorFailureCategory.INVALID_THREAD_ID
                currentIdentity && !currentOwned -> FullMirrorFailureCategory.OWNERSHIP
                !currentIdentity && !cacheMatches -> FullMirrorFailureCategory.CACHED_ROOM_MISMATCH
                !currentIdentity && !legacyOwned -> FullMirrorFailureCategory.AMBIGUOUS_LEGACY_IDENTITY
                else -> FullMirrorFailureCategory.NONE
            }
            remotes += OwnedRemoteSnapshot(
                mirrorKey, reference.messageId, ConversationSnapshotHashGenerator.generate(document.conversation), threadId,
                currentOwned || legacyOwned, true, cacheMatches = cacheMatches, identityCurrent = currentIdentity, reason = reason
            )
        }
        val preview = FullMirrorPreviewPlanner.create(binding, local, remotes, foreignIgnored, device.defaultRegion, now, discoveryReasons)
        persistPreview(database, preview)
        val reasons = FullMirrorFailureCategory.entries.filter { it != FullMirrorFailureCategory.NONE }
            .joinToString(" ") { "${it.name.lowercase()}=${preview.reasonCount(it)}" }
        Log.i("OpenSMSBackup", "full_mirror_preview local=${preview.localConversations} local_classified=${preview.localClassifiedCount} remote_candidates=${preview.remoteCandidates} remote_classified=${preview.remoteClassifiedCount} owned_remote=${preview.ownedRemoteConversations} unchanged=${preview.count(FullMirrorAction.UNCHANGED)} new=${preview.count(FullMirrorAction.UPLOAD_NEW)} replace=${preview.count(FullMirrorAction.REPLACE_CHANGED)} remote_only=${preview.count(FullMirrorAction.TRASH_REMOTE_ONLY)} recover=${preview.count(FullMirrorAction.RECOVER_CACHE)} conflict=${preview.conflicts} foreign_ignored=${preview.foreignIgnored} failed=${preview.failed} limit_exceeded=${preview.blockedReason == FullMirrorFailureCategory.LIMIT_EXCEEDED} $reasons")
        preview
    }

    private suspend fun persistPreview(database: BackupDatabase, preview: FullMirrorPreview) {
        val run = MirrorReconciliationRunEntity(
            preview.binding.runId, preview.binding.profileId, preview.binding.accountIdentity, preview.binding.deviceId,
            preview.binding.deviceLabelId, preview.binding.expectedPolicy, preview.createdAt, preview.expiresAt,
            preview.localDatasetFingerprint, preview.remoteIndexFingerprint, preview.localScanComplete,
            preview.localConversations, preview.ownedRemoteConversations, preview.count(FullMirrorAction.UNCHANGED),
            preview.count(FullMirrorAction.UPLOAD_NEW), preview.count(FullMirrorAction.REPLACE_CHANGED),
            preview.count(FullMirrorAction.TRASH_REMOTE_ONLY), preview.count(FullMirrorAction.RECOVER_CACHE),
            preview.conflicts, preview.foreignIgnored, preview.failed, preview.ownedRemoteConversations,
            preview.count(FullMirrorAction.UPLOAD_NEW) + preview.count(FullMirrorAction.REPLACE_CHANGED),
            preview.trashCount, preview.ownedRemoteConversations * 1_000L + preview.trashCount * 1_000L,
            FullMirrorRunStatus.PREVIEW.name, terminalReason = preview.blockedReason?.name
        )
        val items = preview.items.map { item ->
            MirrorReconciliationItemEntity(
                preview.binding.runId, item.itemId, preview.binding.profileId, preview.binding.accountIdentity,
                preview.binding.deviceId, item.conversationKey, item.androidThreadId, item.action.name,
                item.expectedLocalSourceHash, item.expectedRemoteSnapshotHash, item.priorGmailMessageId,
                FullMirrorItemState.PENDING.name, failureCategory = item.reason.takeUnless { it == FullMirrorFailureCategory.NONE }?.name
            )
        }
        database.mirrorReconciliationDao().insertPreview(run, items)
    }
}