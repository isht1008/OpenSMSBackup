package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.*
import org.junit.Test

class FullMirrorAdditionalSafetyTest {
    @Test fun `account mask never emits full account`() {
        val masked = maskMirrorAccount("mirror.account@example.com")
        assertEquals("m***@e***", masked)
        assertFalse(masked.contains("mirror.account"))
        assertFalse(masked.contains("example.com"))
    }

    @Test fun `remote fingerprint changes when owned remote state changes`() {
        val first = OwnedRemoteSnapshot("key", "message-one", "hash-one", 1, true, true)
        val second = first.copy(snapshotHash = "hash-two")
        assertNotEquals(
            FullMirrorPreviewPlanner.fingerprintRemote(listOf(first)),
            FullMirrorPreviewPlanner.fingerprintRemote(listOf(second))
        )
    }

    @Test fun `more than one hundred Trash actions is high risk`() {
        val binding = FullMirrorBinding("run", "profile-b", "mirror@example.com", "device-b", "label-b", "MIRROR")
        val items = (0..100).map { index ->
            FullMirrorPreviewItem("item-$index", "key-$index", index.toLong(), FullMirrorAction.TRASH_REMOTE_ONLY, null, "hash", "message-$index")
        }
        val preview = FullMirrorPreview(binding, 1, 2, "local", "remote", 0, 0, 101, 0, items, true, remoteCandidates = 101)
        assertTrue(preview.highRisk)
        assertFalse(FullMirrorConfirmationPolicy.evaluate(preview, "MIRROR 100", 1).allowed)
        assertTrue(FullMirrorConfirmationPolicy.evaluate(preview, "MIRROR 101", 1).allowed)
    }
}