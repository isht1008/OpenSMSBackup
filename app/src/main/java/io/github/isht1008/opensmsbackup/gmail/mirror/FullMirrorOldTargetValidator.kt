package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.DeviceSnapshotOwnership
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveDocument

object FullMirrorOldTargetValidator {
    private const val TRASH_LABEL = "TRASH"

    fun inspect(
        binding: FullMirrorBinding,
        item: FullMirrorPreviewItem,
        document: GmailArchiveDocument,
        defaultRegion: String
    ): FullMirrorOldTargetStatus {
        val proof = item.oldTargetProof ?: return FullMirrorOldTargetStatus.INVALID
        if (!proof.isCompleteFor(binding, item)) return FullMirrorOldTargetStatus.INVALID
        if (document.messageId != proof.gmailMessageId ||
            document.messageId == item.resultingGmailMessageId ||
            document.conversation.threadId != proof.androidThreadId ||
            !document.accountEmail.equals(proof.accountIdentity, true) ||
            !document.accountHeader.equals(proof.accountIdentity, true) ||
            document.deviceIdHeader != proof.deviceId ||
            proof.deviceLabelId !in document.labelIds ||
            document.formatVersionHeader != "3" ||
            ConversationSnapshotHashGenerator.generate(document.conversation) != proof.snapshotHash
        ) return FullMirrorOldTargetStatus.INVALID
        if (proof.conversationKeyHeader != null &&
            document.conversationKeyHeader != proof.conversationKeyHeader
        ) return FullMirrorOldTargetStatus.INVALID
        if (proof.identityVersionHeader != null &&
            document.identityVersionHeader != proof.identityVersionHeader
        ) return FullMirrorOldTargetStatus.INVALID

        val owned = when (document.identityVersionHeader) {
            MirrorConversationIdentity.WIRE_NAME -> DeviceSnapshotOwnership.matchesMirrorThread(
                document, proof.profileId, proof.accountIdentity, proof.deviceId,
                proof.deviceLabelId, proof.androidThreadId
            )
            "2", "3" -> DeviceSnapshotOwnership.matchesV2(
                document, proof.accountIdentity, proof.deviceId, proof.deviceLabelId,
                document.conversation, defaultRegion
            )
            else -> false
        }
        if (!owned) return FullMirrorOldTargetStatus.INVALID
        return if (TRASH_LABEL in document.labelIds) {
            FullMirrorOldTargetStatus.ALREADY_TRASHED_VALID
        } else {
            FullMirrorOldTargetStatus.PRESENT_VALID
        }
    }
}
