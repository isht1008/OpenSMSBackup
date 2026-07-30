package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot

enum class FullMirrorAction { UNCHANGED, UPLOAD_NEW, REPLACE_CHANGED, TRASH_REMOTE_ONLY, RECOVER_CACHE, CONFLICT, FAILED }
enum class FullMirrorItemState { PENDING, VALIDATING, UPLOADING, PERSISTED, TRASH_PENDING, COMPLETED, WARNING, FAILED, SKIPPED_CONFLICT }
enum class FullMirrorRunStatus { PREVIEW, CONFIRMED, RUNNING, CANCELLED, COMPLETED, COMPLETED_WITH_WARNINGS, FAILED, STALE }
enum class FullMirrorFailureCategory {
    NONE, INCOMPLETE_LOCAL_SCAN, EMPTY_LOCAL_DATASET, PROFILE_MISMATCH, ACCOUNT_MISMATCH,
    DEVICE_MISMATCH, LABEL_MISMATCH, POLICY_CHANGED, AUTHORIZATION, EXPIRED, LOCAL_CHANGED,
    REMOTE_CHANGED, OWNERSHIP, CONFLICT, UNREADABLE, UPLOAD, PERSISTENCE, TRASH, CANCELLED,
    MISSING_ADDRESS, EMPTY_ADDRESS, PHONE_NORMALIZATION_FAILURE, SHORT_CODE, ALPHANUMERIC_SENDER,
    EMAIL_LIKE_SENDER, GROUP_MULTI_RECIPIENT, INVALID_THREAD_ID, DUPLICATE_LOCAL_IDENTITY,
    DUPLICATE_REMOTE_IDENTITY, AMBIGUOUS_LEGACY_IDENTITY, MISSING_OWNERSHIP_HEADER,
    FORMAT_VERSION_MISMATCH, UNSUPPORTED_IDENTITY_VERSION, CACHED_ROOM_MISMATCH,
    LIMIT_EXCEEDED, FOREGROUND_START_FAILED, OLD_TARGET_PROOF_MISSING,
    OLD_TARGET_MISSING, OLD_TARGET_AMBIGUOUS, REPLACEMENT_NOT_PERSISTED,
    DUPLICATE_REPLACEMENT, UNEXPLAINED_REMOTE_CHANGE, OTHER
}

data class FullMirrorOldTargetProof(
    val profileId: String,
    val accountIdentity: String,
    val deviceId: String,
    val deviceLabelId: String,
    val androidThreadId: Long,
    val gmailMessageId: String,
    val snapshotHash: String,
    val conversationKeyHeader: String? = null,
    val identityVersionHeader: String? = null,
    val formatVersionHeader: String? = null,
    val proofVersion: String = PROOF_VERSION
) {
    fun isCompleteFor(binding: FullMirrorBinding, item: FullMirrorPreviewItem): Boolean =
        profileId == binding.profileId && accountIdentity.equals(binding.accountIdentity, true) &&
            deviceId == binding.deviceId && deviceLabelId == binding.deviceLabelId &&
            androidThreadId > 0L && androidThreadId == item.androidThreadId &&
            gmailMessageId.isNotBlank() && gmailMessageId == item.priorGmailMessageId &&
            snapshotHash.isNotBlank() && snapshotHash == item.expectedRemoteSnapshotHash &&
            proofVersion in setOf(PROOF_VERSION, V8_COMPAT_VERSION)

    companion object {
        const val PROOF_VERSION = "IMMUTABLE_OLD_TARGET_V1"
        const val V8_COMPAT_VERSION = "V8_EXACT_ID_HASH_BINDING"
    }
}

enum class FullMirrorOldTargetStatus { PRESENT_VALID, ALREADY_TRASHED_VALID, MISSING, AMBIGUOUS, INVALID }

data class FullMirrorBinding(
    val runId: String,
    val profileId: String,
    val accountIdentity: String,
    val deviceId: String,
    val deviceLabelId: String,
    val expectedPolicy: String
)

data class CompleteLocalSmsDataset(
    val conversations: List<SmsConversationSnapshot>,
    val messageCount: Int,
    val complete: Boolean,
    val countConsistent: Boolean,
    val failure: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE
) {
    val destructiveSafe: Boolean get() = complete && countConsistent && failure == FullMirrorFailureCategory.NONE
}

data class OwnedRemoteSnapshot(
    val conversationKey: String,
    val messageId: String,
    val snapshotHash: String?,
    val androidThreadId: Long?,
    val ownershipValid: Boolean,
    val readable: Boolean,
    val duplicateCount: Int = 1,
    val cacheMatches: Boolean = false,
    val identityCurrent: Boolean = true,
    val reason: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE,
    val ownershipConversationKeyHeader: String? = null,
    val identityVersionHeader: String? = null,
    val formatVersionHeader: String? = null
)

data class FullMirrorPreviewItem(
    val itemId: String,
    val conversationKey: String,
    val androidThreadId: Long?,
    val action: FullMirrorAction,
    val expectedLocalSourceHash: String?,
    val expectedRemoteSnapshotHash: String?,
    val priorGmailMessageId: String?,
    val reason: FullMirrorFailureCategory = FullMirrorFailureCategory.NONE,
    val oldTargetProof: FullMirrorOldTargetProof? = null,
    val resultingGmailMessageId: String? = null
)

data class FullMirrorPreview(
    val binding: FullMirrorBinding,
    val createdAt: Long,
    val expiresAt: Long,
    val localDatasetFingerprint: String,
    val remoteIndexFingerprint: String,
    val localConversations: Int,
    val sourceMessages: Int,
    val ownedRemoteConversations: Int,
    val foreignIgnored: Int,
    val items: List<FullMirrorPreviewItem>,
    val localScanComplete: Boolean,
    val blockedReason: FullMirrorFailureCategory? = null,
    val remoteCandidates: Int = ownedRemoteConversations,
    val diagnosticReasonCounts: Map<FullMirrorFailureCategory, Int> = emptyMap()
) {
    fun count(action: FullMirrorAction) = items.count { it.action == action }
    fun reasonCount(reason: FullMirrorFailureCategory) =
        diagnosticReasonCounts[reason] ?: items.count { it.reason == reason }
    val trashCount get() = count(FullMirrorAction.TRASH_REMOTE_ONLY) + count(FullMirrorAction.REPLACE_CHANGED)
    val conflicts get() = count(FullMirrorAction.CONFLICT)
    val failed get() = count(FullMirrorAction.FAILED)
    val localClassifiedCount get() = items.count { it.expectedLocalSourceHash != null }
    val remoteClassifiedCount get() = items.count { it.priorGmailMessageId != null }
    val executionAllowed get() = localScanComplete && blockedReason == null && conflicts == 0 && failed == 0 &&
        localClassifiedCount == localConversations && remoteClassifiedCount == remoteCandidates
    val highRisk get() = trashCount > 100 || (ownedRemoteConversations > 0 && trashCount * 10 > ownedRemoteConversations)
}
