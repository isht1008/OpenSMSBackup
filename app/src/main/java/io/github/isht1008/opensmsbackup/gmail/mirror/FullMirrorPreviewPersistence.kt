package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.database.BackupDatabase
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationItemEntity
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationRunEntity

object FullMirrorPreviewPersistence {
    fun runEntity(preview: FullMirrorPreview) = MirrorReconciliationRunEntity(
        preview.binding.runId,
        preview.binding.profileId,
        preview.binding.accountIdentity,
        preview.binding.deviceId,
        preview.binding.deviceLabelId,
        preview.binding.expectedPolicy,
        preview.createdAt,
        preview.expiresAt,
        preview.localDatasetFingerprint,
        preview.remoteIndexFingerprint,
        preview.localScanComplete,
        preview.localConversations,
        preview.ownedRemoteConversations,
        preview.count(FullMirrorAction.UNCHANGED),
        preview.count(FullMirrorAction.UPLOAD_NEW),
        preview.count(FullMirrorAction.REPLACE_CHANGED),
        preview.count(FullMirrorAction.TRASH_REMOTE_ONLY),
        preview.count(FullMirrorAction.RECOVER_CACHE),
        preview.conflicts,
        preview.foreignIgnored,
        preview.failed,
        preview.ownedRemoteConversations,
        preview.count(FullMirrorAction.UPLOAD_NEW) + preview.count(FullMirrorAction.REPLACE_CHANGED),
        preview.trashCount,
        preview.ownedRemoteConversations * 1_000L + preview.trashCount * 1_000L,
        FullMirrorRunStatus.PREVIEW.name,
        terminalReason = preview.blockedReason?.name
    )

    fun itemEntities(preview: FullMirrorPreview) = preview.items.map { item ->
        MirrorReconciliationItemEntity(
            preview.binding.runId,
            item.itemId,
            preview.binding.profileId,
            preview.binding.accountIdentity,
            preview.binding.deviceId,
            item.conversationKey,
            item.androidThreadId,
            item.action.name,
            item.expectedLocalSourceHash,
            item.expectedRemoteSnapshotHash,
            item.priorGmailMessageId,
            FullMirrorItemState.PENDING.name,
            failureCategory = item.reason.takeUnless { it == FullMirrorFailureCategory.NONE }?.name,
            oldTargetProfileId = item.oldTargetProof?.profileId,
            oldTargetAccountIdentity = item.oldTargetProof?.accountIdentity,
            oldTargetDeviceId = item.oldTargetProof?.deviceId,
            oldTargetDeviceLabelId = item.oldTargetProof?.deviceLabelId,
            oldTargetAndroidThreadId = item.oldTargetProof?.androidThreadId,
            oldTargetGmailMessageId = item.oldTargetProof?.gmailMessageId,
            oldTargetSnapshotHash = item.oldTargetProof?.snapshotHash,
            oldTargetConversationKeyHeader = item.oldTargetProof?.conversationKeyHeader,
            oldTargetIdentityVersionHeader = item.oldTargetProof?.identityVersionHeader,
            oldTargetFormatVersionHeader = item.oldTargetProof?.formatVersionHeader,
            oldTargetProofVersion = item.oldTargetProof?.proofVersion
        )
    }

    suspend fun load(database: BackupDatabase, runId: String): FullMirrorPreview? {
        val dao = database.mirrorReconciliationDao()
        val run = dao.findRun(runId) ?: return null
        if (run.status != FullMirrorRunStatus.PREVIEW.name) return null
        val items = dao.findItems(runId).map { item ->
            FullMirrorPreviewItem(
                itemId = item.itemId,
                conversationKey = item.conversationKey,
                androidThreadId = item.androidThreadId,
                action = FullMirrorAction.valueOf(item.action),
                expectedLocalSourceHash = item.expectedLocalSourceHash,
                expectedRemoteSnapshotHash = item.expectedRemoteSnapshotHash,
                priorGmailMessageId = item.priorGmailMessageId,
                reason = item.failureCategory?.let(FullMirrorFailureCategory::valueOf)
                    ?: FullMirrorFailureCategory.NONE,
                oldTargetProof = if (
                    item.oldTargetProfileId != null &&
                    item.oldTargetAccountIdentity != null &&
                    item.oldTargetDeviceId != null &&
                    item.oldTargetDeviceLabelId != null &&
                    item.oldTargetAndroidThreadId != null &&
                    item.oldTargetGmailMessageId != null &&
                    item.oldTargetSnapshotHash != null
                ) FullMirrorOldTargetProof(
                    item.oldTargetProfileId,
                    item.oldTargetAccountIdentity,
                    item.oldTargetDeviceId,
                    item.oldTargetDeviceLabelId,
                    item.oldTargetAndroidThreadId,
                    item.oldTargetGmailMessageId,
                    item.oldTargetSnapshotHash,
                    item.oldTargetConversationKeyHeader,
                    item.oldTargetIdentityVersionHeader,
                    item.oldTargetFormatVersionHeader,
                    item.oldTargetProofVersion ?: FullMirrorOldTargetProof.PROOF_VERSION
                ) else null,
                resultingGmailMessageId = item.resultingGmailMessageId
            )
        }
        return FullMirrorPreview(
            binding = FullMirrorBinding(
                run.runId,
                run.profileId,
                run.accountIdentity,
                run.deviceId,
                run.deviceLabelId,
                run.expectedPolicy
            ),
            createdAt = run.createdAt,
            expiresAt = run.expiresAt,
            localDatasetFingerprint = run.localDatasetFingerprint,
            remoteIndexFingerprint = run.remoteIndexFingerprint,
            localConversations = run.localConversations,
            sourceMessages = 0,
            ownedRemoteConversations = run.ownedRemoteConversations,
            foreignIgnored = run.foreignIgnoredCount,
            items = items,
            localScanComplete = run.localScanComplete,
            blockedReason = run.terminalReason?.let {
                runCatching { FullMirrorFailureCategory.valueOf(it) }.getOrNull()
            },
            remoteCandidates = items.count { it.priorGmailMessageId != null }
        )
    }
}
