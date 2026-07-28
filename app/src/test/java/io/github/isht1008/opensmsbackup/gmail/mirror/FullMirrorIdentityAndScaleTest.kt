package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.device.DeviceProfile
import io.github.isht1008.opensmsbackup.gmail.backup.ArchiveConversationIdentity
import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.*
import org.junit.Test

class FullMirrorIdentityAndScaleTest {
    private val binding = FullMirrorBinding("run", "profile-b", "mirror@example.com", "device-b", "label-b", "MIRROR")

    @Test fun `3330 conversations conserve exactly with no truncation`() {
        val local = conversations(3_330)
        val preview = preview(local)
        assertEquals(3_330, preview.count(FullMirrorAction.UPLOAD_NEW))
        assertEquals(3_330, preview.localClassifiedCount)
        assertEquals(0, preview.remoteClassifiedCount)
        assertTrue(preview.executionAllowed)
    }

    @Test fun `10000 conversations are supported and boundary overflow is explicit without truncation`() {
        val atLimit = preview(conversations(10_000))
        assertEquals(10_000, atLimit.localClassifiedCount)
        assertNull(atLimit.blockedReason)
        val over = preview(conversations(10_001))
        assertEquals(10_001, over.localClassifiedCount)
        assertEquals(FullMirrorFailureCategory.LIMIT_EXCEEDED, over.blockedReason)
        assertFalse(over.executionAllowed)
    }

    @Test fun `thirteen cached legacy owned snapshots migrate only as replacements`() {
        val local = conversations(13)
        val remotes = local.mapIndexed { index, c -> OwnedRemoteSnapshot(
            MirrorConversationIdentity.key(binding.profileId, binding.accountIdentity, binding.deviceId, c.threadId),
            "legacy-$index", ConversationSnapshotHashGenerator.generate(c), c.threadId,
            ownershipValid = true, readable = true, cacheMatches = true, identityCurrent = false
        ) }
        val preview = FullMirrorPreviewPlanner.create(binding, CompleteLocalSmsDataset(local, local.size, true, true), remotes, 0, "US", 1)
        assertEquals(13, preview.count(FullMirrorAction.REPLACE_CHANGED))
        assertEquals(0, preview.conflicts)
        assertTrue(preview.executionAllowed)
    }

    @Test fun `uncached ambiguous legacy snapshot remains conflict`() {
        val c = conversations(1).single()
        val remote = OwnedRemoteSnapshot(
            MirrorConversationIdentity.key(binding.profileId, binding.accountIdentity, binding.deviceId, c.threadId),
            "legacy", ConversationSnapshotHashGenerator.generate(c), c.threadId,
            ownershipValid = false, readable = true, cacheMatches = false, identityCurrent = false,
            reason = FullMirrorFailureCategory.CACHED_ROOM_MISMATCH
        )
        val preview = FullMirrorPreviewPlanner.create(binding, CompleteLocalSmsDataset(listOf(c), 1, true, true), listOf(remote), 0, "US", 1)
        assertEquals(FullMirrorAction.CONFLICT, preview.items.single().action)
    }

    @Test fun `Mirror MIME uses thread identity while Archive default remains V3`() {
        val c = conversations(1).single()
        val device = DeviceProfile("device-b", "maker", "model", null, "16", null, null, "device", 1, 1, "US")
        val snapshotHash = ConversationSnapshotHashGenerator.generate(c)
        val mirrorKey = MirrorConversationIdentity.key(binding.profileId, binding.accountIdentity, binding.deviceId, c.threadId)
        val mirror = ConversationMimeMessageBuilder().build(c, binding.accountIdentity, snapshotHash, device, mirrorKey, MirrorConversationIdentity.WIRE_NAME)
        assertEquals(mirrorKey, mirror.headers[OpenSmsHeaders.CONVERSATION_KEY])
        assertEquals(MirrorConversationIdentity.WIRE_NAME, mirror.headers[OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION])
        val archive = ConversationMimeMessageBuilder().build(c, binding.accountIdentity, snapshotHash, device)
        assertEquals(ArchiveConversationIdentity.key(ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS, c.address, binding.accountIdentity, device.deviceId, device.defaultRegion), archive.headers[OpenSmsHeaders.CONVERSATION_KEY])
        assertEquals("3", archive.headers[OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION])
    }

    @Test fun `blocked preview hides confirmation and safe Trash preview requires typed confirmation`() {
        val conflict = preview(conversations(1)).copy(items = listOf(
            preview(conversations(1)).items.single().copy(action = FullMirrorAction.CONFLICT, reason = FullMirrorFailureCategory.CONFLICT)
        ))
        val blocked = FullMirrorPreviewPresentation.resolve(conflict)
        assertFalse(blocked.showConfirmationAction)
        assertFalse(blocked.showTypedConfirmation)
        assertEquals("Close", blocked.primaryActionLabel)
        val remote = OwnedRemoteSnapshot("key", "id", "hash", 9, true, true)
        val safeTrash = FullMirrorPreview(
            binding, 1, 2, "local", "remote", 0, 0, 1, 0,
            listOf(FullMirrorPreviewItem("item", "key", 9, FullMirrorAction.TRASH_REMOTE_ONLY, null, "hash", "id")),
            true, remoteCandidates = 1
        )
        val safe = FullMirrorPreviewPresentation.resolve(safeTrash)
        assertTrue(safe.showConfirmationAction)
        assertTrue(safe.showTypedConfirmation)
    }

    private fun preview(local: List<SmsConversationSnapshot>) = FullMirrorPreviewPlanner.create(
        binding, CompleteLocalSmsDataset(local, local.size, true, true), emptyList(), 0, "US", 1
    )
    private fun conversations(count: Int) = (1..count).map { index ->
        val id = index.toLong()
        val sms = SmsMessage(id, id, "SENDER-$index", null, "synthetic", id, "date", 1, null, true, null)
        SmsConversationSnapshot(id, "SENDER-$index", null, listOf(sms))
    }
}