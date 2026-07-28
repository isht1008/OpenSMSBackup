package io.github.isht1008.opensmsbackup.gmail.mirror

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FullMirrorExecutorTest {
    @Test fun `replacement strictly uploads persists then Trashes`() = runBlocking {
        val events = mutableListOf<String>()
        val item = item(FullMirrorAction.REPLACE_CHANGED, "old")
        val journal = Journal()
        val summary = FullMirrorExecutor(Gateway(events), journal).execute(preview(item))
        assertEquals(listOf("binding", "binding", "local", "ownership", "upload", "persist", "ownership", "trash:old"), events)
        assertEquals(1, summary.changedReplaced)
        assertEquals(1, summary.previousTrashed)
    }

    @Test fun `upload or persistence failure never Trashes previous`() = runBlocking {
        val uploadEvents = mutableListOf<String>()
        FullMirrorExecutor(Gateway(uploadEvents, failUpload = true), Journal()).execute(preview(item(FullMirrorAction.REPLACE_CHANGED, "old")))
        assertFalse(uploadEvents.any { it.startsWith("trash") })
        val persistEvents = mutableListOf<String>()
        FullMirrorExecutor(Gateway(persistEvents, failPersist = true), Journal()).execute(preview(item(FullMirrorAction.REPLACE_CHANGED, "old")))
        assertFalse(persistEvents.any { it.startsWith("trash") })
    }

    @Test fun `Trash failure leaves persisted replacement warning and resume performs Trash only`() = runBlocking {
        val journal = Journal()
        val firstEvents = mutableListOf<String>()
        val item = item(FullMirrorAction.REPLACE_CHANGED, "old")
        val first = FullMirrorExecutor(Gateway(firstEvents, failTrash = true), journal).execute(preview(item))
        assertEquals(1, first.warnings)
        assertTrue(firstEvents.contains("persist"))
        val secondEvents = mutableListOf<String>()
        FullMirrorExecutor(Gateway(secondEvents), journal).execute(preview(item))
        assertFalse(secondEvents.contains("upload"))
        assertFalse(secondEvents.contains("persist"))
        assertTrue(secondEvents.contains("trash:old"))
    }

    @Test fun `remote-only requires ownership before recoverable Trash and never exposes delete API`() = runBlocking {
        val events = mutableListOf<String>()
        FullMirrorExecutor(Gateway(events), Journal()).execute(preview(item(FullMirrorAction.TRASH_REMOTE_ONLY, "remote")))
        assertEquals(listOf("binding", "binding", "ownership", "trash:remote", "remove-cache"), events)
    }

    @Test fun `completed journal items are not repeated`() = runBlocking {
        val journal = Journal(mutableMapOf("item" to FullMirrorItemState.COMPLETED))
        val events = mutableListOf<String>()
        FullMirrorExecutor(Gateway(events), journal).execute(preview(item(FullMirrorAction.UPLOAD_NEW)))
        assertEquals(listOf("binding", "binding"), events)
    }

    @Test fun `ambiguous uploading journal state is never uploaded again`() = runBlocking {
        val journal = Journal(mutableMapOf("item" to FullMirrorItemState.UPLOADING))
        val events = mutableListOf<String>()
        val summary = FullMirrorExecutor(Gateway(events), journal).execute(preview(item(FullMirrorAction.UPLOAD_NEW)))
        assertFalse(events.contains("upload"))
        assertEquals(1, summary.failed)
    }

    @Test fun `ownership failure stops later destructive actions`() = runBlocking {
        val events = mutableListOf<String>()
        val gateway = object : FullMirrorMutationGateway {
            override suspend fun validateBinding(binding: FullMirrorBinding) = true.also { events += "binding" }
            override suspend fun currentLocalSourceHash(item: FullMirrorPreviewItem) = "local"
            override suspend fun validateOwnedRemote(binding: FullMirrorBinding, item: FullMirrorPreviewItem) = false.also { events += "ownership" }
            override suspend fun upload(item: FullMirrorPreviewItem) = error("unexpected upload")
            override suspend fun persistUploaded(item: FullMirrorPreviewItem, resultingMessageId: String) = Unit
            override suspend fun trashOwned(messageId: String) { events += "trash" }
            override suspend fun persistRemoteOnlyRemoval(item: FullMirrorPreviewItem) = Unit
            override suspend fun recoverCache(item: FullMirrorPreviewItem) = Unit
        }
        val preview = preview(item(FullMirrorAction.TRASH_REMOTE_ONLY, "one")).copy(remoteCandidates = 2, ownedRemoteConversations = 2, items = listOf(
            item(FullMirrorAction.TRASH_REMOTE_ONLY, "one"),
            item(FullMirrorAction.TRASH_REMOTE_ONLY, "two").copy(itemId = "second")
        ))
        runCatching { FullMirrorExecutor(gateway, Journal()).execute(preview) }
        assertEquals(1, events.count { it == "ownership" })
        assertFalse(events.contains("trash"))
    }
    private fun preview(item: FullMirrorPreviewItem): FullMirrorPreview {
        val remoteCount = if (item.priorGmailMessageId != null) 1 else 0
        return FullMirrorPreview(
            FullMirrorBinding("run", "profile-b", "mirror@example.com", "device-b", "label-b", "MIRROR"),
            1, 1000, "local", "remote", if (item.expectedLocalSourceHash != null) 1 else 0, 1,
            remoteCount, 0, listOf(item), true, remoteCandidates = remoteCount
        )
    }
    private fun item(action: FullMirrorAction, previous: String? = null) = FullMirrorPreviewItem(
        "item", "key", 1, action,
        if (action == FullMirrorAction.TRASH_REMOTE_ONLY) null else "local", "remote", previous
    )

    private class Journal(private val states: MutableMap<String, FullMirrorItemState> = mutableMapOf()) : FullMirrorExecutionJournal {
        override suspend fun state(itemId: String) = states[itemId] ?: FullMirrorItemState.PENDING
        override suspend fun mark(itemId: String, state: FullMirrorItemState, resultingMessageId: String?, warning: FullMirrorFailureCategory?) { states[itemId] = state }
    }
    private class Gateway(
        val events: MutableList<String>, val failUpload: Boolean = false,
        val failPersist: Boolean = false, val failTrash: Boolean = false
    ) : FullMirrorMutationGateway {
        override suspend fun validateBinding(binding: FullMirrorBinding) = true.also { events += "binding" }
        override suspend fun currentLocalSourceHash(item: FullMirrorPreviewItem) = "local".also { events += "local" }
        override suspend fun validateOwnedRemote(binding: FullMirrorBinding, item: FullMirrorPreviewItem) = true.also { events += "ownership" }
        override suspend fun upload(item: FullMirrorPreviewItem): String { events += "upload"; if (failUpload) error("upload"); return "new" }
        override suspend fun persistUploaded(item: FullMirrorPreviewItem, resultingMessageId: String) { events += "persist"; if (failPersist) error("persist") }
        override suspend fun trashOwned(messageId: String) { events += "trash:$messageId"; if (failTrash) error("trash") }
        override suspend fun persistRemoteOnlyRemoval(item: FullMirrorPreviewItem) { events += "remove-cache" }
        override suspend fun recoverCache(item: FullMirrorPreviewItem) { events += "recover-cache" }
    }
}
