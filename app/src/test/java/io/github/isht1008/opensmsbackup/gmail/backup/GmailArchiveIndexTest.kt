package io.github.isht1008.opensmsbackup.gmail.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailArchiveIndexTest {
    @Test fun `pagination deduplicates IDs and terminates on null token`() = runBlocking {
        val source = FakeSource(
            pages = mapOf(
                null to GmailArchiveMessagePage(listOf("a", "b"), "next"),
                "next" to GmailArchiveMessagePage(listOf("b", "c"), null)
            ),
            metadata = listOf(
                metadata("a", v3Key, 1),
                metadata("b", v3Key, 3),
                metadata("c", v2Key, 2, "2")
            ).associateBy { it.messageId }
        )
        val progress = mutableListOf<Int>()
        val index = build(source) { scanned, _ -> progress += scanned }

        assertEquals(listOf(null, "next"), source.tokens)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), source.batches)
        assertEquals(listOf(1, 2, 3), progress)
        assertEquals(listOf("b", "a", "c"), index.candidates(v3Key, v2Key).map { it.messageId })
    }

    @Test fun `foreign malformed and unsupported metadata are rejected`() = runBlocking {
        val entries = listOf(
            metadata("valid", v3Key, 1),
            metadata("account", v3Key, 2).copy(accountHeader = "other@example.com"),
            metadata("device", v3Key, 3).copy(deviceIdHeader = "other"),
            metadata("label", v3Key, 4).copy(labelIds = setOf("other")),
            metadata("format", v3Key, 5).copy(formatVersionHeader = "99"),
            metadata("identity", v3Key, 6).copy(identityVersionHeader = "1"),
            metadata("key", "malformed", 7)
        )
        val index = build(FakeSource.single(entries))
        assertEquals(listOf("valid"), index.candidates(v3Key, v2Key).map { it.messageId })
    }

    @Test fun `V3 precedes V2 and newest then message ID ordering is stable`() = runBlocking {
        val index = build(FakeSource.single(listOf(
            metadata("v2", v2Key, 100, "2"),
            metadata("v3-b", v3Key, 20),
            metadata("v3-a", v3Key, 20),
            metadata("v3-old", v3Key, 10)
        )))
        assertEquals(
            listOf("v3-a", "v3-b", "v3-old", "v2"),
            index.candidates(v3Key, v2Key).map { it.messageId }
        )
    }

    @Test fun `candidate memory is bounded per identity`() = runBlocking {
        val index = build(FakeSource.single((1L..10L).map { metadata("id-$it", v3Key, it) }))
        assertEquals(4, index.candidates(v3Key, v2Key).size)
        assertEquals("id-10", index.candidates(v3Key, v2Key).first().messageId)
    }

    @Test(expected = CancellationException::class)
    fun `listing cancellation propagates`() = runBlocking<Unit> {
        build(FakeSource(listFailure = CancellationException()))
    }

    @Test(expected = CancellationException::class)
    fun `metadata cancellation propagates`() = runBlocking<Unit> {
        build(FakeSource(
            pages = mapOf(null to GmailArchiveMessagePage(listOf("message"), null)),
            metadataFailure = CancellationException()
        ))
    }

    private suspend fun build(
        source: GmailArchiveIndexSource,
        progress: suspend (Int, Int) -> Unit = { _, _ -> }
    ) = GmailArchiveIndex.build(source, ACCOUNT, DEVICE, LABEL, progress)

    private fun metadata(id: String, key: String, date: Long, identity: String = "3") =
        GmailArchiveMetadataReference(
            id, "thread-$id", date, setOf(LABEL), ACCOUNT, DEVICE, key, identity, "3"
        )

    private val v3Key = ArchiveConversationIdentity.key(
        ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
        "+1 555 123 4567", ACCOUNT, DEVICE, "US"
    )
    private val v2Key = ArchiveConversationIdentity.key(
        ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
        "+1 555 123 4567", ACCOUNT, DEVICE
    )

    private class FakeSource(
        private val pages: Map<String?, GmailArchiveMessagePage> = emptyMap(),
        private val metadata: Map<String, GmailArchiveMetadataReference> = emptyMap(),
        private val listFailure: Throwable? = null,
        private val metadataFailure: Throwable? = null
    ) : GmailArchiveIndexSource {
        val tokens = mutableListOf<String?>()
        val batches = mutableListOf<List<String>>()
        override suspend fun listPage(deviceLabelId: String, pageToken: String?): GmailArchiveMessagePage {
            listFailure?.let { throw it }
            tokens += pageToken
            return requireNotNull(pages[pageToken])
        }
        override suspend fun readMetadataPage(messageIds: List<String>): List<GmailArchiveMetadataReference> {
            metadataFailure?.let { throw it }
            batches += messageIds
            return messageIds.mapNotNull(metadata::get)
        }
        companion object {
            fun single(values: List<GmailArchiveMetadataReference>): FakeSource {
                val metadata = values.associateBy { it.messageId }
                return FakeSource(
                    pages = mapOf(null to GmailArchiveMessagePage(metadata.keys.toList(), null)),
                    metadata = metadata
                )
            }
        }
    }

    private companion object {
        const val ACCOUNT = "user@example.com"
        const val DEVICE = "device-a"
        const val LABEL = "device-label"
    }
}
