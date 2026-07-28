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

    @Test fun `valid cached Gmail message does not initialize lazy index`() = runBlocking {
        val lookup = FakeLookup(mapOf("cached" to Result.success(document("cached", 10))))
        var indexBuilds = 0
        val lazy = LazyGmailArchiveIndex {
            indexBuilds++
            GmailArchiveIndex.build(
                EmptyIndexSource,
                "user@example.com",
                "device-a",
                "device-label"
            )
        }
        val locator = GmailArchiveLocator(
            lookup,
            "user@example.com",
            "device-a",
            "device-label",
            indexProvider = lazy::get
        )

        assertEquals("cached", locator.locate(expected, "cached").getOrThrow()?.messageId)
        assertEquals(0, indexBuilds)
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

    @Test fun `cached snapshot from another device is rejected and search recovers current device`() = runBlocking {
        val other = document("other", 30).copy(
            deviceIdHeader = "device-b",
            conversationKeyHeader = v2Key("device-b"),
            labelIds = setOf("other-label")
        )
        val lookup = FakeLookup(
            reads = mapOf(
                "other" to Result.success(other),
                "current" to Result.success(document("current", 20))
            ),
            candidates = listOf(candidate("current"))
        )
        assertEquals(
            "current",
            locator(lookup).locate(expected, "other").getOrThrow()?.messageId
        )
    }

    @Test fun `search containing two devices selects only current device`() = runBlocking {
        val other = document("other", 50).copy(
            deviceIdHeader = "device-b",
            conversationKeyHeader = v2Key("device-b"),
            labelIds = setOf("device-label")
        )
        val lookup = FakeLookup(
            reads = mapOf(
                "other" to Result.success(other),
                "current" to Result.success(document("current", 10))
            ),
            candidates = listOf(candidate("other"), candidate("current"))
        )
        assertEquals("current", locator(lookup).locate(expected, null).getOrThrow()?.messageId)
    }

    @Test fun `legacy V1 archive requires explicit Room cache and cannot be search claimed`() = runBlocking {
        val legacy = document("legacy", 10).copy(
            deviceIdHeader = null,
            identityVersionHeader = null,
            labelIds = emptySet(),
            conversationKeyHeader = ArchiveConversationIdentity.key(
                expected.address,
                "user@example.com"
            )
        )
        val cachedLookup = FakeLookup(mapOf("legacy" to Result.success(legacy)))
        assertEquals(
            "legacy",
            locator(cachedLookup).locate(expected, "legacy").getOrThrow()?.messageId
        )
        val searchLookup = FakeLookup(
            mapOf("legacy" to Result.success(legacy)),
            listOf(candidate("legacy"))
        )
        assertNull(locator(searchLookup).locate(expected, null).getOrThrow())
    }

    private fun locator(lookup: GmailArchiveLookup) =
        GmailArchiveLocator(lookup, "user@example.com", "device-a", "device-label")

    private fun document(
        id: String,
        internalDate: Long,
        address: String = "+1 555 123 4567"
    ) = GmailArchiveDocument(
        messageId = id,
        threadId = "thread-$id",
        internalDate = internalDate,
        accountEmail = "user@example.com",
        conversationKeyHeader = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
            address,
            "user@example.com",
            "device-a"
        ),
        conversation = conversation(address),
        deviceIdHeader = "device-a",
        identityVersionHeader = "2",
        labelIds = setOf("device-label")
    )

    private fun candidate(id: String) = GmailArchiveCandidate(id, "thread-$id")

    private fun v2Key(deviceId: String) = ArchiveConversationIdentity.key(
        ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
        expected.address,
        "user@example.com",
        deviceId
    )

    private fun conversation(address: String) =
        SmsConversationSnapshot(7, address, null, emptyList())

    private class FakeLookup(
        private val reads: Map<String, Result<GmailArchiveDocument>>,
        private val candidates: List<GmailArchiveCandidate> = emptyList()
    ) : GmailArchiveLookup {
        var searches = 0
        override suspend fun read(messageId: String) =
            reads[messageId] ?: Result.failure(ArchiveMessageNotFoundException())
        override suspend fun search(
            conversationKey: String,
            deviceLabelId: String
        ): Result<List<GmailArchiveCandidate>> {
            searches++
            return Result.success(candidates)
        }
    }

    private object EmptyIndexSource : GmailArchiveIndexSource {
        override suspend fun listPage(deviceLabelId: String, pageToken: String?) =
            GmailArchiveMessagePage(emptyList(), null)
        override suspend fun readMetadataPage(messageIds: List<String>) =
            emptyList<GmailArchiveMetadataReference>()
    }
}
