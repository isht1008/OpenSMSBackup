package io.github.isht1008.opensmsbackup.gmail.backup

import kotlinx.coroutines.runBlocking
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GmailArchiveRequestBudgetTest {
    @Test fun `3314 covered exact legacy rows are satisfied before Gmail construction`() {
        val conversations = (1L..3_314L).map { threadId ->
            SmsConversationSnapshot(threadId, "address", null, emptyList())
        }
        val snapshots = conversations.associate { conversation ->
            conversation.threadId to ConversationSnapshotEntity(
                id = conversation.threadId,
                accountId = ACCOUNT,
                accountEmail = ACCOUNT,
                androidThreadId = conversation.threadId,
                address = "address",
                messageCount = 0,
                snapshotHash = ConversationSnapshotHashGenerator.generate(conversation),
                localSourceHash = null,
                gmailMessageId = "cached",
                firstMessageDate = 0L,
                lastMessageDate = 0L,
                backupTime = 1_000L
            )
        }
        val result = ArchiveLocalCheckpointClassifier.classify(
            conversations,
            snapshots,
            ACCOUNT,
            ACCOUNT,
            DEVICE,
            completedFullBackupTime = 2_000L
        )
        assertEquals(3_314, result.locallyBootstrappable.size)
        assertEquals(3_314, result.locallyUnchanged.size)
        assertEquals(0, result.requiringComparison.size)
    }

    @Test fun `3329 uncached conversations perform no searches or full reads`() = runBlocking {
        val lookup = CountingLookup()
        val index = GmailArchiveIndex.build(EmptySource, ACCOUNT, DEVICE, LABEL)
        val locator = GmailArchiveLocator(lookup, ACCOUNT, DEVICE, LABEL, "US", index)

        repeat(3_329) { position ->
            assertNull(locator.locate(conversation("sender-$position"), null).getOrThrow())
        }

        assertEquals(0, lookup.searches)
        assertEquals(0, lookup.reads)
    }

    @Test fun `V1 remains usable only through exact Room linked message ID`() = runBlocking {
        val legacyConversation = conversation("sender")
        val legacy = GmailArchiveDocument(
            messageId = "legacy",
            threadId = "thread",
            internalDate = 1,
            accountEmail = ACCOUNT,
            conversationKeyHeader = ArchiveConversationIdentity.key("sender", ACCOUNT),
            conversation = legacyConversation
        )
        val lookup = CountingLookup(mapOf("legacy" to Result.success(legacy)))
        val index = GmailArchiveIndex.build(EmptySource, ACCOUNT, DEVICE, LABEL)
        val locator = GmailArchiveLocator(lookup, ACCOUNT, DEVICE, LABEL, "US", index)

        assertEquals("legacy", locator.locate(legacyConversation, "legacy").getOrThrow()?.messageId)
        assertEquals(1, lookup.reads)
        assertNull(locator.locate(legacyConversation, null).getOrThrow())
        assertEquals(1, lookup.reads)
        assertEquals(0, lookup.searches)
    }

    private fun conversation(address: String) =
        SmsConversationSnapshot(7, address, null, emptyList())

    private class CountingLookup(
        private val documents: Map<String, Result<GmailArchiveDocument>> = emptyMap()
    ) : GmailArchiveLookup {
        var searches = 0
        var reads = 0
        override suspend fun read(messageId: String): Result<GmailArchiveDocument> {
            reads++
            return documents[messageId] ?: Result.failure(ArchiveMessageNotFoundException())
        }
        override suspend fun search(
            conversationKey: String,
            deviceLabelId: String
        ): Result<List<GmailArchiveCandidate>> {
            searches++
            return Result.success(emptyList())
        }
    }

    private object EmptySource : GmailArchiveIndexSource {
        override suspend fun listPage(deviceLabelId: String, pageToken: String?) =
            GmailArchiveMessagePage(emptyList(), null)
        override suspend fun readMetadataPage(messageIds: List<String>) =
            emptyList<GmailArchiveMetadataReference>()
    }

    private companion object {
        const val ACCOUNT = "user@example.com"
        const val DEVICE = "device-a"
        const val LABEL = "device-label"
    }
}
