package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveDocument
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class FullMirrorOldTargetValidatorTest {
    private val binding = FullMirrorBinding("plan", "profile-b", "b@example.test", "device-b", "label-b", "MIRROR")
    private val conversation = SmsConversationSnapshot(7, "fixture", null, emptyList())
    private val oldKey = MirrorConversationIdentity.key(binding.profileId, binding.accountIdentity, binding.deviceId, 7)
    private val hash = ConversationSnapshotHashGenerator.generate(conversation)
    private val proof = FullMirrorOldTargetProof(
        binding.profileId, binding.accountIdentity, binding.deviceId, binding.deviceLabelId,
        7, "old", hash, oldKey, MirrorConversationIdentity.WIRE_NAME, "3"
    )
    private val item = FullMirrorPreviewItem(
        "item", oldKey, 7, FullMirrorAction.REPLACE_CHANGED, "local", hash, "old",
        oldTargetProof = proof, resultingGmailMessageId = "new"
    )
    private val document = GmailArchiveDocument(
        "old", null, 1, binding.accountIdentity, oldKey, conversation,
        binding.deviceId, MirrorConversationIdentity.WIRE_NAME, setOf(binding.deviceLabelId),
        accountHeader = binding.accountIdentity, formatVersionHeader = "3"
    )

    @Test fun immutableProofValidatesAfterRoomPointsAtReplacement() {
        assertEquals(FullMirrorOldTargetStatus.PRESENT_VALID, FullMirrorOldTargetValidator.inspect(binding, item, document, "US"))
    }

    @Test fun alreadyTrashedOwnedTargetIsIdempotentlyComplete() {
        assertEquals(
            FullMirrorOldTargetStatus.ALREADY_TRASHED_VALID,
            FullMirrorOldTargetValidator.inspect(binding, item, document.copy(labelIds = setOf(binding.deviceLabelId, "TRASH")), "US")
        )
    }

    @Test fun mismatchedOwnershipFieldsBlockCleanup() {
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item.copy(oldTargetProof = null), document, "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding.copy(profileId = "profile-a"), item, document, "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding.copy(accountIdentity = "a@example.test"), item, document, "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(accountHeader = "a@example.test"), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(deviceIdHeader = "other"), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(labelIds = setOf("other")), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(conversation = conversation.copy(threadId = 8)), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(conversationKeyHeader = "foreign"), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(identityVersionHeader = "2"), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(formatVersionHeader = "2"), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(messageId = "new"), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item.copy(resultingGmailMessageId = "old"), document, "US"))
    }

    @Test fun fingerprintMismatchAndCachePointerAloneCannotAuthorizeTrash() {
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item, document.copy(conversation = conversation.copy(address = "changed")), "US"))
        assertEquals(FullMirrorOldTargetStatus.INVALID, FullMirrorOldTargetValidator.inspect(binding, item.copy(oldTargetProof = null), document, "US"))
    }
}
