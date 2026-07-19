package io.github.isht1008.opensmsbackup.gmail.backup

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.MessagePart
import io.github.isht1008.opensmsbackup.gmail.error.GmailOperationException
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy
import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.Base64

class GmailArchivedConversationReader(
    private val gmail: Gmail,
    private val profileId: String,
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy(),
    private val onRetry: suspend (Int, Int) -> Unit = { _, _ -> },
    private val parser: ArchivedConversationParser = ArchivedConversationParser()
) : GmailArchiveLookup {
    override suspend fun read(messageId: String): Result<GmailArchiveDocument> =
        withContext(Dispatchers.IO) {
            try {
                val message = retryPolicy.execute(
                    operationName = "get_archive_snapshot",
                    profileId = profileId,
                    onRetry = onRetry
                ) {
                    gmail.users().messages().get("me", messageId)
                        .setFormat("full")
                        .execute()
                }
                val part = requireNotNull(findSnapshotPart(message.payload)) {
                    "Gmail conversation snapshot attachment is missing."
                }
                val encoded = part.body?.data ?: part.body?.attachmentId?.let { attachmentId ->
                    retryPolicy.execute(
                        operationName = "get_archive_attachment",
                        profileId = profileId,
                        onRetry = onRetry
                    ) {
                        gmail.users().messages().attachments()
                            .get("me", messageId, attachmentId)
                            .execute().data
                    }
                }
                require(!encoded.isNullOrBlank()) {
                    "Gmail conversation snapshot attachment is empty."
                }
                val parsed = parser.parseArchive(
                    String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
                )
                Result.success(
                    GmailArchiveDocument(
                        messageId = requireNotNull(message.id),
                        threadId = message.threadId,
                        internalDate = message.internalDate ?: 0L,
                        accountEmail = parsed.accountEmail,
                        conversationKeyHeader = message.payload?.headers
                            ?.firstOrNull {
                                it.name.equals(OpenSmsHeaders.CONVERSATION_KEY, ignoreCase = true)
                            }?.value,
                        conversation = parsed.conversation,
                        deviceIdHeader = header(message.payload, OpenSmsHeaders.DEVICE_ID),
                        identityVersionHeader = header(
                            message.payload,
                            OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION
                        ),
                        labelIds = message.labelIds.orEmpty().toSet(),
                        defaultRegionHeader = header(message.payload, OpenSmsHeaders.DEFAULT_REGION)
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
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
        part?.headers?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

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

    private companion object {
        const val SNAPSHOT_MIME_TYPE =
            "application/vnd.opensmsbackup.conversation+json"
        const val KEYED_CANDIDATE_LIMIT = 25L
        const val TOTAL_CANDIDATE_LIMIT = 50L
    }
}
