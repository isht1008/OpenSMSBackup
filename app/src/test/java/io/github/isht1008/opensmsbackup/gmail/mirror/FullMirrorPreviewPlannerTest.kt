package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.*
import org.junit.Test

class FullMirrorPreviewPlannerTest {
    private val binding = FullMirrorBinding("run-b", "profile-b", "mirror@example.com", "device-b", "label-b", "MIRROR")

    @Test fun `preview classifies current and legacy snapshots without mutation`() {
        val unchanged = conversation(1, "+1 202 555 0101", "one")
        val changed = conversation(2, "SERVICE", "new")
        val upload = conversation(3, "12345", "new")
        val recovery = conversation(4, "sender", "same")
        val remotes = listOf(
            remote(unchanged, "m1", cache = true),
            remote(conversation(2, changed.address!!, "old"), "m2"),
            remote(recovery, "m4", cache = false),
            remote(conversation(5, "other", "gone"), "m5"),
            remote(conversation(6, "legacy", "same"), "m6", cache = true, current = false)
        )
        val local = complete(unchanged, changed, upload, recovery, conversation(6, "legacy", "same"))
        val preview = FullMirrorPreviewPlanner.create(binding, local, remotes, 7, "US", 100)
        assertEquals(1, preview.count(FullMirrorAction.UNCHANGED))
        assertEquals(1, preview.count(FullMirrorAction.UPLOAD_NEW))
        assertEquals(2, preview.count(FullMirrorAction.REPLACE_CHANGED))
        assertEquals(1, preview.count(FullMirrorAction.TRASH_REMOTE_ONLY))
        assertEquals(1, preview.count(FullMirrorAction.RECOVER_CACHE))
        assertEquals(local.conversations.size, preview.localClassifiedCount)
        assertEquals(remotes.size, preview.remoteClassifiedCount)
        assertEquals(7, preview.foreignIgnored)
    }

    @Test fun `incomplete or unexpectedly empty local scan blocks remote-only Trash`() {
        val remote = remote(conversation(9, "sender", "remote"), "m")
        val incomplete = FullMirrorPreviewPlanner.create(binding, CompleteLocalSmsDataset(emptyList(), 0, false, false, FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN), listOf(remote), 0, "US", 1)
        assertFalse(incomplete.executionAllowed)
        assertEquals(FullMirrorAction.FAILED, incomplete.items.single().action)
        val empty = FullMirrorPreviewPlanner.create(binding, CompleteLocalSmsDataset(emptyList(), 0, true, true), listOf(remote), 0, "US", 1)
        assertEquals(FullMirrorFailureCategory.EMPTY_LOCAL_DATASET, empty.blockedReason)
    }

    @Test fun `short code alphanumeric and non normalizable senders upload safely`() {
        listOf("12345", "BANKALERT", "???").forEachIndexed { index, address ->
            val preview = FullMirrorPreviewPlanner.create(binding, complete(conversation((index + 1).toLong(), address, "x")), emptyList(), 0, "US", 1)
            assertEquals(FullMirrorAction.UPLOAD_NEW, preview.items.single().action)
            assertTrue(preview.executionAllowed)
        }
    }

    @Test fun `invalid thread and duplicate local or remote identity remain conflicts`() {
        val invalid = FullMirrorPreviewPlanner.create(binding, complete(conversation(0, "sender", "x")), emptyList(), 0, "US", 1)
        assertEquals(FullMirrorFailureCategory.INVALID_THREAD_ID, invalid.items.single().reason)
        val duplicateLocalConversation = conversation(2, "sender", "x")
        val duplicateLocal = FullMirrorPreviewPlanner.create(binding, complete(duplicateLocalConversation, duplicateLocalConversation), emptyList(), 0, "US", 1)
        assertEquals(2, duplicateLocal.localClassifiedCount)
        assertEquals(2, duplicateLocal.conflicts)
        val phone = conversation(3, "sender", "x")
        val r = remote(phone, "a")
        val duplicateRemote = FullMirrorPreviewPlanner.create(binding, complete(phone), listOf(r, r.copy(messageId = "b")), 0, "US", 1)
        assertEquals(1, duplicateRemote.localClassifiedCount)
        assertEquals(2, duplicateRemote.remoteClassifiedCount)
        assertEquals(3, duplicateRemote.conflicts)
    }

    @Test fun `typed confirmation expiry and risk thresholds are enforced`() {
        val local = conversation(1, "sender", "new")
        val old = remote(conversation(1, local.address!!, "old"), "old")
        val preview = FullMirrorPreviewPlanner.create(binding, complete(local), listOf(old), 0, "US", 1_000)
        assertFalse(FullMirrorConfirmationPolicy.evaluate(preview, "MIRROR 2", 1_001).allowed)
        assertTrue(FullMirrorConfirmationPolicy.evaluate(preview, "MIRROR 1", 1_001).allowed)
        assertFalse(FullMirrorConfirmationPolicy.evaluate(preview, "MIRROR 1", preview.expiresAt + 1).allowed)
    }

    @Test fun `account and device bindings produce distinct thread identities`() {
        val key = MirrorConversationIdentity.key("profile-b", "mirror@example.com", "device-b", 7)
        assertNotEquals(key, MirrorConversationIdentity.key("profile-a", "archive@example.com", "device-b", 7))
        assertNotEquals(key, MirrorConversationIdentity.key("profile-b", "mirror@example.com", "device-a", 7))
        assertNotEquals(key, MirrorConversationIdentity.key("profile-b", "mirror@example.com", "device-b", 8))
    }

    @Test fun `foreign ownership invalid remote never becomes destructive`() {
        val c = conversation(1, "sender", "x")
        val invalid = remote(c, "m").copy(ownershipValid = false, reason = FullMirrorFailureCategory.OWNERSHIP)
        val preview = FullMirrorPreviewPlanner.create(binding, complete(c), listOf(invalid), 1, "US", 1)
        assertEquals(FullMirrorAction.CONFLICT, preview.items.single().action)
        assertEquals(0, preview.trashCount)
    }

    internal fun complete(vararg c: SmsConversationSnapshot) = CompleteLocalSmsDataset(c.toList(), c.sumOf { it.messageCount }, true, true)
    internal fun remote(c: SmsConversationSnapshot, id: String, cache: Boolean = false, current: Boolean = true) = OwnedRemoteSnapshot(
        MirrorConversationIdentity.key(binding.profileId, binding.accountIdentity, binding.deviceId, c.threadId), id,
        ConversationSnapshotHashGenerator.generate(c), c.threadId, true, true, cacheMatches = cache, identityCurrent = current
    )
    internal fun conversation(thread: Long, address: String?, body: String): SmsConversationSnapshot {
        val sms = SmsMessage(thread, thread, address, null, body, 1000 + thread, "date", 1, null, true, null)
        return SmsConversationSnapshot(thread, address, null, listOf(sms))
    }
}