package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.LocalConversationSourceHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.sms.NormalizedSmsAddress
import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object FullMirrorPreviewPlanner {
    const val PREVIEW_TTL_MILLIS = 15 * 60 * 1000L
    const val MAX_SUPPORTED_CONVERSATIONS = 10_000

    fun create(
        binding: FullMirrorBinding,
        local: CompleteLocalSmsDataset,
        remotes: List<OwnedRemoteSnapshot>,
        foreignIgnored: Int,
        defaultRegion: String,
        now: Long,
        discoveryReasons: Map<FullMirrorFailureCategory, Int> = emptyMap()
    ): FullMirrorPreview {
        require(binding.profileId.isNotBlank() && binding.accountIdentity.isNotBlank())
        require(binding.deviceId.isNotBlank() && binding.deviceLabelId.isNotBlank())
        require(binding.expectedPolicy == "MIRROR")

        val localEntries = local.conversations.map { conversation ->
            val key = if (conversation.threadId > 0) {
                MirrorConversationIdentity.key(binding.profileId, binding.accountIdentity, binding.deviceId, conversation.threadId)
            } else digest("invalid-thread\u0000${LocalConversationSourceHashGenerator.generate(conversation)}")
            key to conversation
        }
        val localByKey = localEntries.groupBy({ it.first }, { it.second })
        val remoteByKey = remotes.groupBy { it.conversationKey }
        val items = mutableListOf<FullMirrorPreviewItem>()
        val consumedRemoteKeys = mutableSetOf<String>()

        localEntries.forEachIndexed { ordinal, (key, conversation) ->
            val locals = localByKey.getValue(key)
            val remoteMatches = remoteByKey[key].orEmpty()
            when {
                conversation.threadId <= 0L -> items += localConflict(binding, key, conversation, ordinal, FullMirrorFailureCategory.INVALID_THREAD_ID)
                locals.size > 1 -> items += localConflict(binding, key, conversation, ordinal, FullMirrorFailureCategory.DUPLICATE_LOCAL_IDENTITY)
                remoteMatches.size > 1 -> items += localConflict(binding, key, conversation, ordinal, FullMirrorFailureCategory.DUPLICATE_REMOTE_IDENTITY)
                remoteMatches.size == 1 -> {
                    consumedRemoteKeys += key
                    items += classifyMatched(binding, key, conversation, remoteMatches.single(), ordinal)
                }
                else -> items += localItem(binding, key, conversation, ordinal, FullMirrorAction.UPLOAD_NEW)
            }
        }

        remotes.forEachIndexed { ordinal, remote ->
            val duplicateLocal = localByKey[remote.conversationKey].orEmpty().size > 1
            val duplicateRemote = remoteByKey[remote.conversationKey].orEmpty().size > 1
            val matchedOneToOne = remote.conversationKey in consumedRemoteKeys && !duplicateLocal && !duplicateRemote
            if (!matchedOneToOne) {
                val action = when {
                    duplicateRemote || duplicateLocal -> FullMirrorAction.CONFLICT
                    !remote.ownershipValid || !remote.readable -> FullMirrorAction.CONFLICT
                    local.destructiveSafe -> FullMirrorAction.TRASH_REMOTE_ONLY
                    else -> FullMirrorAction.FAILED
                }
                val reason = when {
                    duplicateRemote -> FullMirrorFailureCategory.DUPLICATE_REMOTE_IDENTITY
                    duplicateLocal -> FullMirrorFailureCategory.DUPLICATE_LOCAL_IDENTITY
                    remote.reason != FullMirrorFailureCategory.NONE -> remote.reason
                    !remote.readable -> FullMirrorFailureCategory.UNREADABLE
                    !remote.ownershipValid -> FullMirrorFailureCategory.OWNERSHIP
                    !local.destructiveSafe -> FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN
                    else -> FullMirrorFailureCategory.NONE
                }
                items += remoteItem(binding, remote, ordinal, action, reason)
            }
        }

        val localDiagnostics = local.conversations.mapNotNull { addressReason(it, defaultRegion) }
            .groupingBy { it }.eachCount()
        val reasonCounts = (discoveryReasons.keys + localDiagnostics.keys + items.map { it.reason })
            .filter { it != FullMirrorFailureCategory.NONE }
            .associateWith { reason ->
                (discoveryReasons[reason] ?: 0) + (localDiagnostics[reason] ?: 0) + items.count { it.reason == reason }
            }
        val limitExceeded = local.conversations.size > MAX_SUPPORTED_CONVERSATIONS || remotes.size > MAX_SUPPORTED_CONVERSATIONS
        val blocked = when {
            !local.destructiveSafe -> local.failure.takeUnless { it == FullMirrorFailureCategory.NONE }
                ?: FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN
            local.conversations.isEmpty() && remotes.any { it.ownershipValid } -> FullMirrorFailureCategory.EMPTY_LOCAL_DATASET
            limitExceeded -> FullMirrorFailureCategory.LIMIT_EXCEEDED
            else -> null
        }
        val preview = FullMirrorPreview(
            binding, now, now + PREVIEW_TTL_MILLIS,
            fingerprintLocal(local.conversations), fingerprintRemote(remotes),
            local.conversations.size, local.messageCount, remotes.count { it.ownershipValid }, foreignIgnored,
            items, local.destructiveSafe, blocked, remotes.size, reasonCounts
        )
        check(preview.localClassifiedCount == local.conversations.size) { "Full Mirror local classification conservation failed." }
        check(preview.remoteClassifiedCount == remotes.size) { "Full Mirror remote classification conservation failed." }
        return preview
    }

    private fun classifyMatched(binding: FullMirrorBinding, key: String, local: SmsConversationSnapshot, remote: OwnedRemoteSnapshot, ordinal: Int): FullMirrorPreviewItem {
        if (!remote.readable) return combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.UNREADABLE)
        if (!remote.ownershipValid) return combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.CONFLICT, remote.reason.takeUnless { it == FullMirrorFailureCategory.NONE } ?: FullMirrorFailureCategory.OWNERSHIP)
        val currentHash = ConversationSnapshotHashGenerator.generate(local)
        return when {
            !remote.identityCurrent -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.REPLACE_CHANGED)
            remote.snapshotHash == null -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.UNREADABLE)
            remote.snapshotHash == currentHash && remote.cacheMatches -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.UNCHANGED)
            remote.snapshotHash == currentHash -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.RECOVER_CACHE)
            else -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.REPLACE_CHANGED)
        }
    }

    private fun localItem(binding: FullMirrorBinding, key: String, local: SmsConversationSnapshot, ordinal: Int, action: FullMirrorAction, reason: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE) =
        FullMirrorPreviewItem(digest("${binding.runId}\u0000local\u0000$key\u0000$ordinal"), key, local.threadId, action, LocalConversationSourceHashGenerator.generate(local), null, null, reason)
    private fun localConflict(binding: FullMirrorBinding, key: String, local: SmsConversationSnapshot, ordinal: Int, reason: FullMirrorFailureCategory) =
        localItem(binding, key, local, ordinal, FullMirrorAction.CONFLICT, reason)
    private fun remoteItem(binding: FullMirrorBinding, remote: OwnedRemoteSnapshot, ordinal: Int, action: FullMirrorAction, reason: FullMirrorFailureCategory) =
        FullMirrorPreviewItem(digest("${binding.runId}\u0000remote\u0000${remote.conversationKey}\u0000$ordinal"), remote.conversationKey, remote.androidThreadId, action, null, remote.snapshotHash, remote.messageId, reason, proof(binding, remote))
    private fun combinedItem(binding: FullMirrorBinding, key: String, local: SmsConversationSnapshot, remote: OwnedRemoteSnapshot, ordinal: Int, action: FullMirrorAction, reason: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE) =
        FullMirrorPreviewItem(digest("${binding.runId}\u0000both\u0000$key\u0000$ordinal"), key, local.threadId, action, LocalConversationSourceHashGenerator.generate(local), remote.snapshotHash, remote.messageId, reason, proof(binding, remote))

    private fun proof(binding: FullMirrorBinding, remote: OwnedRemoteSnapshot): FullMirrorOldTargetProof? {
        val threadId = remote.androidThreadId ?: return null
        val hash = remote.snapshotHash ?: return null
        if (!remote.ownershipValid || remote.messageId.isBlank()) return null
        return FullMirrorOldTargetProof(
            binding.profileId, binding.accountIdentity, binding.deviceId, binding.deviceLabelId,
            threadId, remote.messageId, hash, remote.ownershipConversationKeyHeader,
            remote.identityVersionHeader, remote.formatVersionHeader
        )
    }

    private fun addressReason(c: SmsConversationSnapshot, region: String): FullMirrorFailureCategory? {
        val address = c.address
        if (address == null) return FullMirrorFailureCategory.MISSING_ADDRESS
        if (address.isBlank()) return FullMirrorFailureCategory.EMPTY_ADDRESS
        if (address.contains(',') || address.contains(';')) return FullMirrorFailureCategory.GROUP_MULTI_RECIPIENT
        if (address.contains('@')) return FullMirrorFailureCategory.EMAIL_LIKE_SENDER
        return when (SmsAddressNormalizer().normalize(address, region)) {
            is NormalizedSmsAddress.PhoneNumber -> null
            is NormalizedSmsAddress.ShortCode -> FullMirrorFailureCategory.SHORT_CODE
            is NormalizedSmsAddress.SenderId -> FullMirrorFailureCategory.ALPHANUMERIC_SENDER
            is NormalizedSmsAddress.Unknown -> FullMirrorFailureCategory.PHONE_NORMALIZATION_FAILURE
        }
    }

    fun fingerprintLocal(conversations: List<SmsConversationSnapshot>) = digest(
        conversations.sortedBy { it.threadId }.joinToString("\u0000") { "${it.threadId}:${LocalConversationSourceHashGenerator.generate(it)}" }
    )
    fun fingerprintRemote(remotes: List<OwnedRemoteSnapshot>) = digest(
        remotes.sortedWith(compareBy({ it.conversationKey }, { it.messageId })).joinToString("\u0000") {
            "${it.conversationKey}:${it.messageId}:${it.snapshotHash}:${it.ownershipValid}:${it.readable}:${it.identityCurrent}"
        }
    )
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
