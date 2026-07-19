package io.github.isht1008.opensmsbackup.gmail.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GmailArchiveLocatorTest {
    private val expected = conversation("+1 555 123 4567")

    @Test fun `cached Gmail message exists and is valid`() = runBlocking {
        val lookup = FakeLookup(mapOf("cached" to Result.success(document("cached", 10))))
        val result = locator(lookup).locate(expected, "cached").getOrThrow()
        assertEquals("cached", result?.messageId)
        assertEquals(0, lookup.searches)
    }

    @Test fun `cached 404 falls back to Gmail search`() = runBlocking {
        val lookup = FakeLookup(
            reads = mapOf(
                "cached" to Result.failure(ArchiveMessageNotFoundException()),
                "recovered" to Result.success(document("recovered", 20))
            ),
            candidates = listOf(candidate("recovered"))
        )
        assertEquals(
            "recovered",
            locator(lookup).locate(expected, "cached").getOrThrow()?.messageId
        )
    }

    @Test fun `cached message without valid attachment falls back to search`() = runBlocking {
        val lookup = FakeLookup(
            reads = mapOf(
                "cached" to Result.failure(InvalidArchiveSnapshotException("missing")),
                "valid" to Result.success(document("valid", 20))
            ),
            candidates = listOf(candidate("valid"))
        )
        assertEquals("valid", locator(lookup).locate(expected, "cached").getOrThrow()?.messageId)
    }

    @Test fun `cached message from another conversation falls back to search`() = runBlocking {
        val lookup = FakeLookup(
            reads = mapOf(
                "cached" to Result.success(document("cached", 30, "999")),
                "valid" to Result.success(document("valid", 20))
            ),
            candidates = listOf(candidate("valid"))
        )
        assertEquals("valid", locator(lookup).locate(expected, "cached").getOrThrow()?.messageId)
    }

    @Test fun `newest valid search candidate is selected`() = runBlocking {
        val lookup = FakeLookup(
            reads = mapOf(
                "older" to Result.success(document("older", 10)),
                "newer" to Result.success(document("newer", 30))
            ),
            candidates = listOf(candidate("older"), candidate("newer"))
        )
        assertEquals("newer", locator(lookup).locate(expected, null).getOrThrow()?.messageId)
    }

    @Test fun `malformed newest candidate falls through to older valid candidate`() = runBlocking {
        val lookup = FakeLookup(
            reads = mapOf(
                "newest" to Result.failure(InvalidArchiveSnapshotException("malformed")),
                "older" to Result.success(document("older", 10))
            ),
            candidates = listOf(candidate("newest"), candidate("older"))
        )
        assertEquals("older", locator(lookup).locate(expected, null).getOrThrow()?.messageId)
    }

    @Test fun `no Room snapshot can recover Gmail archive`() = runBlocking {
        val lookup = FakeLookup(
            reads = mapOf("found" to Result.success(document("found", 10))),
            candidates = listOf(candidate("found"))
        )
        assertEquals("found", locator(lookup).locate(expected, null).getOrThrow()?.messageId)
    }

    @Test fun `no Room snapshot and no Gmail archive returns empty archive`() = runBlocking {
        assertNull(locator(FakeLookup(emptyMap())).locate(expected, null).getOrThrow())
    }

    private fun locator(lookup: GmailArchiveLookup) =
        GmailArchiveLocator(lookup, "user@example.com")

    private fun document(
        id: String,
        internalDate: Long,
        address: String = "+1 555 123 4567"
    ) = GmailArchiveDocument(
        messageId = id,
        threadId = "thread-$id",
        internalDate = internalDate,
        accountEmail = "user@example.com",
        conversationKeyHeader = null,
        conversation = conversation(address)
    )

    private fun candidate(id: String) = GmailArchiveCandidate(id, "thread-$id")

    private fun conversation(address: String) =
        SmsConversationSnapshot(7, address, null, emptyList())

    private class FakeLookup(
        private val reads: Map<String, Result<GmailArchiveDocument>>,
        private val candidates: List<GmailArchiveCandidate> = emptyList()
    ) : GmailArchiveLookup {
        var searches = 0
        override suspend fun read(messageId: String) =
            reads[messageId] ?: Result.failure(ArchiveMessageNotFoundException())
        override suspend fun search(conversationKey: String): Result<List<GmailArchiveCandidate>> {
            searches++
            return Result.success(candidates)
        }
    }
}
