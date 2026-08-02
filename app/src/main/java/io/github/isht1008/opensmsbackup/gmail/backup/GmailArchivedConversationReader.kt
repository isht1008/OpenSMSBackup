package io.github.isht1008.opensmsbackup.gmail.backup

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.MessagePart
import io.github.isht1008.opensmsbackup.gmail.error.GmailOperationException
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy
import io.github.isht1008.opensmsbackup.gmail.error.GmailReadThrottle
import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.Base64

class GmailArchivedConversationReader(
    private val gmail: Gmail,
    private val profileId: String,
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy(),
    private val onRetry: suspend (Int, Int) -> Unit = { _, _ -> },
    private val parser: ArchivedConversationParser = ArchivedConversationParser(),
    private val readThrottle: GmailReadThrottle = GmailReadThrottle(),
    private val metadataConcurrency: Int = DEFAULT_METADATA_CONCURRENCY,
    private val onFullSnapshotRead: () -> Unit = {},
    private val onMetadataRead: () -> Unit = {},
    private val onTiming: (operation: String, durationMillis: Long, success: Boolean) -> Unit =
        { _, _, _ -> }
) : GmailArchiveLookup, GmailArchiveIndexSource {
    init {
        require(metadataConcurrency in 1..MAX_METADATA_CONCURRENCY)
    }
    override suspend fun read(messageId: String): Result<GmailArchiveDocument> =
        withContext(Dispatchers.IO) {
            onFullSnapshotRead()
            try {
                val messageStarted = monotonicMillis()
                val message = retryPolicy.execute(
                    operationName = "get_archive_snapshot",
                    profileId = profileId,
                    onRetry = onRetry
                ) {
                    gmail.users().messages().get("me", messageId)
                        .setFormat("full")
                        .execute()
                }.also {
                    onTiming(
                        "cached_snapshot_read",
                        monotonicMillis() - messageStarted,
                        true
                    )
                }
                val part = requireNotNull(findSnapshotPart(message.payload)) {
                    "Gmail conversation snapshot attachment is missing."
                }
                val encoded = part.body?.data ?: part.body?.attachmentId?.let { attachmentId ->
                    val attachmentStarted = monotonicMillis()
                    retryPolicy.execute(
                        operationName = "get_archive_attachment",
                        profileId = profileId,
                        onRetry = onRetry
                    ) {
                        gmail.users().messages().attachments()
                            .get("me", messageId, attachmentId)
                            .execute().data
                    }.also {
                        onTiming(
                            "attachment_download",
                            monotonicMillis() - attachmentStarted,
                            true
                        )
                    }
                }
                require(!encoded.isNullOrBlank()) {
                    "Gmail conversation snapshot attachment is empty."
                }
                val parseStarted = monotonicMillis()
                val parsed = parser.parseArchive(
                    String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
                ).also {
                    onTiming(
                        "archive_decode_parse",
                        monotonicMillis() - parseStarted,
                        true
                    )
                }
                Result.success(
                    GmailArchiveDocument(
                        messageId = requireNotNull(message.id),
                        threadId = message.threadId,
                        internalDate = message.internalDate ?: 0L,
                        accountEmail = parsed.accountEmail,
                        conversationKeyHeader = header(
                            message.payload,
                            OpenSmsHeaders.CONVERSATION_KEY
                        ),
                        conversation = parsed.conversation,
                        deviceIdHeader = header(message.payload, OpenSmsHeaders.DEVICE_ID),
                        identityVersionHeader = header(
                            message.payload,
                            OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION
                        ),
                        labelIds = message.labelIds.orEmpty().toSet(),
                        defaultRegionHeader = header(message.payload, OpenSmsHeaders.DEFAULT_REGION),
                        accountHeader = header(message.payload, OpenSmsHeaders.ACCOUNT),
                        formatVersionHeader = header(message.payload, OpenSmsHeaders.VERSION)
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onTiming("cached_snapshot_pipeline", 0L, false)
                Result.failure(mapReadFailure(error))
            }
        }

    override suspend fun search(
        conversationKey: String,
        deviceLabelId: String
    ): Result<List<GmailArchiveCandidate>> = withContext(Dispatchers.IO) {
        try {
            val keyed = listMessages(
                labelId = deviceLabelId,
                query = "\"${OpenSmsHeaders.CONVERSATION_KEY}: $conversationKey\"",
                maximum = KEYED_CANDIDATE_LIMIT
            )
            val legacy = listMessages(
                labelId = deviceLabelId,
                query = null,
                maximum = TOTAL_CANDIDATE_LIMIT
            )
            Result.success(
                (keyed + legacy).distinctBy { it.messageId }
                    .take(TOTAL_CANDIDATE_LIMIT.toInt())
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun listPage(
        deviceLabelId: String,
        pageToken: String?
    ): GmailArchiveMessagePage = withContext(Dispatchers.IO) {
        val response = retryPolicy.execute(
            operationName = "list_archive_index_page",
            profileId = profileId,
            onRetry = onRetry,
            onBackoff = readThrottle::defer
        ) {
            val waited = readThrottle.awaitPermission()
            if (waited > 0L) onTiming("read_throttle_wait", waited, true)
            gmail.users().messages().list("me")
                .setLabelIds(listOf(deviceLabelId))
                .setMaxResults(INDEX_PAGE_SIZE)
                .setPageToken(pageToken)
                .execute()
        }
        GmailArchiveMessagePage(
            messageIds = response.messages.orEmpty().mapNotNull { it.id },
            nextPageToken = response.nextPageToken
        )
    }

    override suspend fun readMetadataPage(
        messageIds: List<String>
    ): List<GmailArchiveMetadataReference> = coroutineScope {
        val result = mutableListOf<Pair<Int, GmailArchiveMetadataReference?>>()
        GmailMetadataBatching.batches(messageIds.withIndex().toList(), metadataConcurrency)
            .forEach { batch ->
            result += batch.map { indexed ->
                async(Dispatchers.IO) {
                    indexed.index to readMetadata(indexed.value)
                }
            }.awaitAll()
        }
        result
            .sortedBy { it.first }
            .mapNotNull { it.second }
    }

    private suspend fun readMetadata(
        messageId: String
    ): GmailArchiveMetadataReference? {
        onMetadataRead()
        return try {
            val message = retryPolicy.execute(
                operationName = "read_archive_index_metadata",
                profileId = profileId,
                onRetry = onRetry,
                onBackoff = readThrottle::defer
            ) {
                val waited = readThrottle.awaitPermission()
                if (waited > 0L) onTiming("read_throttle_wait", waited, true)
                gmail.users().messages().get("me", messageId)
                    .setFormat("metadata")
                    .setMetadataHeaders(INDEX_METADATA_HEADERS)
                    .execute()
            }
            GmailArchiveMetadataReference(
                messageId = requireNotNull(message.id),
                threadId = message.threadId,
                internalDate = message.internalDate ?: 0L,
                labelIds = message.labelIds.orEmpty().toSet(),
                accountHeader = header(message.payload, OpenSmsHeaders.ACCOUNT),
                deviceIdHeader = header(message.payload, OpenSmsHeaders.DEVICE_ID),
                conversationKeyHeader = header(message.payload, OpenSmsHeaders.CONVERSATION_KEY),
                identityVersionHeader = header(
                    message.payload,
                    OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION
                ),
                formatVersionHeader = header(message.payload, OpenSmsHeaders.VERSION),
                androidThreadIdHeader = header(message.payload, OpenSmsHeaders.THREAD_ID)
                    ?.toLongOrNull(),
                snapshotHashHeader = header(message.payload, OpenSmsHeaders.SNAPSHOT_HASH)
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: GmailOperationException) {
            if (error.failure.httpStatusCode == 404) null else throw error
        }
    }

    private suspend fun listMessages(
        labelId: String,
        query: String?,
        maximum: Long
    ): List<GmailArchiveCandidate> {
        val response = retryPolicy.execute(
            operationName = "search_archive_snapshots",
            profileId = profileId,
            onRetry = onRetry
        ) {
            gmail.users().messages().list("me")
                .setLabelIds(listOf(labelId))
                .setQ(query)
                .setMaxResults(maximum)
                .execute()
        }
        return response.messages.orEmpty().mapNotNull { message ->
            message.id?.let { GmailArchiveCandidate(it, message.threadId) }
        }
    }

    private fun findSnapshotPart(part: MessagePart?): MessagePart? {
        if (part == null) return null
        if (
            part.mimeType == SNAPSHOT_MIME_TYPE ||
            part.filename?.startsWith("opensms-conversation-") == true &&
                part.filename?.endsWith(".json") == true
        ) return part
        return part.parts.orEmpty().firstNotNullOfOrNull(::findSnapshotPart)
    }

    private fun header(part: MessagePart?, name: String): String? =
        GmailArchiveHeaderReader.uniqueValue(part, name)

    private fun mapReadFailure(error: Exception): Throwable {
        if (error is GmailOperationException && error.failure.httpStatusCode == 404) {
            return ArchiveMessageNotFoundException()
        }
        if (error is IllegalArgumentException || error is org.json.JSONException) {
            return InvalidArchiveSnapshotException(
                "Gmail message does not contain a valid OpenSMSBackup snapshot.",
                error
            )
        }
        return error
    }

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

    private companion object {
        const val SNAPSHOT_MIME_TYPE =
            "application/vnd.opensmsbackup.conversation+json"
        const val KEYED_CANDIDATE_LIMIT = 25L
        const val TOTAL_CANDIDATE_LIMIT = 50L
        const val INDEX_PAGE_SIZE = 500L
        const val DEFAULT_METADATA_CONCURRENCY = 4
        const val MAX_METADATA_CONCURRENCY = 24
        val INDEX_METADATA_HEADERS = listOf(
            OpenSmsHeaders.ACCOUNT,
            OpenSmsHeaders.DEVICE_ID,
            OpenSmsHeaders.CONVERSATION_KEY,
            OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION,
            OpenSmsHeaders.VERSION,
            OpenSmsHeaders.THREAD_ID,
            OpenSmsHeaders.SNAPSHOT_HASH
        )
    }
}

internal object GmailArchiveHeaderReader {
    fun uniqueValue(part: MessagePart?, name: String): String? {
        val matches = part?.headers.orEmpty()
            .filter { it.name.equals(name, ignoreCase = true) }
        return matches.singleOrNull()?.value
    }
}

object GmailMetadataBatching {
    fun <T> batches(items: List<T>, maximumScheduled: Int): List<List<T>> {
        require(maximumScheduled > 0)
        return items.chunked(maximumScheduled)
    }
}
