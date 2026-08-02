package io.github.isht1008.opensmsbackup.gmail.mirror

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object FullMirrorScalarPreviewPlanner {
    fun create(
        binding: FullMirrorBinding,
        locals: List<FullMirrorLocalScalar>,
        sourceMessages: Int,
        localComplete: Boolean,
        localCountConsistent: Boolean,
        localFailure: FullMirrorFailureCategory,
        remotes: List<OwnedRemoteSnapshot>,
        foreignIgnored: Int,
        now: Long,
        discoveryReasons: Map<FullMirrorFailureCategory, Int> = emptyMap()
    ): FullMirrorPreview {
        require(binding.profileId.isNotBlank() && binding.accountIdentity.isNotBlank())
        require(binding.deviceId.isNotBlank() && binding.deviceLabelId.isNotBlank())
        require(binding.expectedPolicy == "MIRROR")
        val destructiveSafe = localComplete && localCountConsistent &&
            localFailure == FullMirrorFailureCategory.NONE
        val localEntries = locals.map { local ->
            val key = if (local.androidThreadId > 0L) {
                MirrorConversationIdentity.key(
                    binding.profileId,
                    binding.accountIdentity,
                    binding.deviceId,
                    local.androidThreadId
                )
            } else {
                digest("invalid-thread\u0000${local.localSourceHash}")
            }
            key to local
        }
        val localByKey = localEntries.groupBy({ it.first }, { it.second })
        val remoteByKey = remotes.groupBy { it.conversationKey }
        val items = mutableListOf<FullMirrorPreviewItem>()
        val consumedRemoteKeys = mutableSetOf<String>()

        localEntries.forEachIndexed { ordinal, (key, local) ->
            val localMatches = localByKey.getValue(key)
            val remoteMatches = remoteByKey[key].orEmpty()
            when {
                local.androidThreadId <= 0L ->
                    items += localItem(binding, key, local, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.INVALID_THREAD_ID)
                localMatches.size > 1 ->
                    items += localItem(binding, key, local, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.DUPLICATE_LOCAL_IDENTITY)
                remoteMatches.size > 1 ->
                    items += localItem(binding, key, local, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.DUPLICATE_REMOTE_IDENTITY)
                remoteMatches.size == 1 -> {
                    consumedRemoteKeys += key
                    items += classifyMatched(binding, key, local, remoteMatches.single(), ordinal)
                }
                else -> items += localItem(binding, key, local, ordinal, FullMirrorAction.UPLOAD_NEW)
            }
        }

        remotes.forEachIndexed { ordinal, remote ->
            val duplicateLocal = localByKey[remote.conversationKey].orEmpty().size > 1
            val duplicateRemote = remoteByKey[remote.conversationKey].orEmpty().size > 1
            val matchedOneToOne = remote.conversationKey in consumedRemoteKeys &&
                !duplicateLocal && !duplicateRemote
            if (!matchedOneToOne) {
                val action = when {
                    duplicateRemote || duplicateLocal -> FullMirrorAction.CONFLICT
                    !remote.ownershipValid || !remote.readable -> FullMirrorAction.CONFLICT
                    destructiveSafe -> FullMirrorAction.TRASH_REMOTE_ONLY
                    else -> FullMirrorAction.FAILED
                }
                val reason = when {
                    duplicateRemote -> FullMirrorFailureCategory.DUPLICATE_REMOTE_IDENTITY
                    duplicateLocal -> FullMirrorFailureCategory.DUPLICATE_LOCAL_IDENTITY
                    remote.reason != FullMirrorFailureCategory.NONE -> remote.reason
                    !remote.readable -> FullMirrorFailureCategory.UNREADABLE
                    !remote.ownershipValid -> FullMirrorFailureCategory.OWNERSHIP
                    !destructiveSafe -> FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN
                    else -> FullMirrorFailureCategory.NONE
                }
                items += remoteItem(binding, remote, ordinal, action, reason)
            }
        }

        val localReasons = locals.mapNotNull { it.diagnosticReason }.groupingBy { it }.eachCount()
        val reasonCounts = (discoveryReasons.keys + localReasons.keys + items.map { it.reason })
            .filter { it != FullMirrorFailureCategory.NONE }
            .associateWith { reason ->
                (discoveryReasons[reason] ?: 0) + (localReasons[reason] ?: 0) +
                    items.count { it.reason == reason }
            }
        val limitExceeded = locals.size > FullMirrorPreviewPlanner.MAX_SUPPORTED_CONVERSATIONS ||
            remotes.size > FullMirrorPreviewPlanner.MAX_SUPPORTED_CONVERSATIONS
        val blocked = when {
            !destructiveSafe -> localFailure.takeUnless { it == FullMirrorFailureCategory.NONE }
                ?: FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN
            locals.isEmpty() && remotes.any { it.ownershipValid } ->
                FullMirrorFailureCategory.EMPTY_LOCAL_DATASET
            limitExceeded -> FullMirrorFailureCategory.LIMIT_EXCEEDED
            else -> null
        }
        val preview = FullMirrorPreview(
            binding = binding,
            createdAt = now,
            expiresAt = now + FullMirrorPreviewPlanner.PREVIEW_TTL_MILLIS,
            localDatasetFingerprint = fingerprintLocal(locals),
            remoteIndexFingerprint = FullMirrorPreviewPlanner.fingerprintRemote(remotes),
            localConversations = locals.size,
            sourceMessages = sourceMessages,
            ownedRemoteConversations = remotes.count { it.ownershipValid },
            foreignIgnored = foreignIgnored,
            items = items,
            localScanComplete = destructiveSafe,
            blockedReason = blocked,
            remoteCandidates = remotes.size,
            diagnosticReasonCounts = reasonCounts
        )
        check(preview.localClassifiedCount == locals.size)
        check(preview.remoteClassifiedCount == remotes.size)
        return preview
    }

    fun fingerprintLocal(locals: List<FullMirrorLocalScalar>): String = digest(
        locals.sortedBy { it.androidThreadId }.joinToString("\u0000") {
            "${it.androidThreadId}:${it.localSourceHash}"
        }
    )

    private fun classifyMatched(
        binding: FullMirrorBinding,
        key: String,
        local: FullMirrorLocalScalar,
        remote: OwnedRemoteSnapshot,
        ordinal: Int
    ): FullMirrorPreviewItem = when {
        !remote.readable -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.UNREADABLE)
        !remote.ownershipValid -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.CONFLICT, remote.reason.takeUnless { it == FullMirrorFailureCategory.NONE } ?: FullMirrorFailureCategory.OWNERSHIP)
        !remote.identityCurrent -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.REPLACE_CHANGED)
        remote.snapshotHash == null -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.CONFLICT, FullMirrorFailureCategory.UNREADABLE)
        remote.snapshotHash == local.snapshotHash && remote.cacheMatches -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.UNCHANGED)
        remote.snapshotHash == local.snapshotHash -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.RECOVER_CACHE)
        else -> combinedItem(binding, key, local, remote, ordinal, FullMirrorAction.REPLACE_CHANGED)
    }

    private fun localItem(binding: FullMirrorBinding, key: String, local: FullMirrorLocalScalar, ordinal: Int, action: FullMirrorAction, reason: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE) =
        FullMirrorPreviewItem(digest("${binding.runId}\u0000local\u0000$key\u0000$ordinal"), key, local.androidThreadId, action, local.localSourceHash, null, null, reason)

    private fun remoteItem(binding: FullMirrorBinding, remote: OwnedRemoteSnapshot, ordinal: Int, action: FullMirrorAction, reason: FullMirrorFailureCategory) =
        FullMirrorPreviewItem(digest("${binding.runId}\u0000remote\u0000${remote.conversationKey}\u0000$ordinal"), remote.conversationKey, remote.androidThreadId, action, null, remote.snapshotHash, remote.messageId, reason, proof(binding, remote))

    private fun combinedItem(binding: FullMirrorBinding, key: String, local: FullMirrorLocalScalar, remote: OwnedRemoteSnapshot, ordinal: Int, action: FullMirrorAction, reason: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE) =
        FullMirrorPreviewItem(digest("${binding.runId}\u0000both\u0000$key\u0000$ordinal"), key, local.androidThreadId, action, local.localSourceHash, remote.snapshotHash, remote.messageId, reason, proof(binding, remote))

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

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
