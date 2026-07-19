package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.gmail.error.GmailOperationException

data class GmailArchiveCandidate(
    val messageId: String,
    val threadId: String?
)

data class GmailArchiveDocument(
    val messageId: String,
    val threadId: String?,
    val internalDate: Long,
    val accountEmail: String,
    val conversationKeyHeader: String?,
    val conversation: SmsConversationSnapshot
)

interface GmailArchiveLookup {
    suspend fun read(messageId: String): Result<GmailArchiveDocument>
    suspend fun search(conversationKey: String): Result<List<GmailArchiveCandidate>>
}

class ArchiveMessageNotFoundException : Exception("Gmail archive message was not found.")
class InvalidArchiveSnapshotException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class GmailArchiveLocator(
    private val lookup: GmailArchiveLookup,
    private val accountEmail: String
) {
    suspend fun locate(
        conversation: SmsConversationSnapshot,
        cachedMessageId: String?
    ): Result<GmailArchiveDocument?> {
        val expectedKey = ArchiveConversationIdentity.key(
            conversation.address,
            accountEmail
        )

        cachedMessageId?.takeIf { it.isNotBlank() }?.let { messageId ->
            val cached = lookup.read(messageId)
            cached.getOrNull()?.takeIf { isValid(it, expectedKey) }?.let {
                return Result.success(it)
            }
        }

        val candidates = lookup.search(expectedKey).getOrElse {
            return Result.failure(it)
        }
        var newest: GmailArchiveDocument? = null

        candidates.distinctBy { it.messageId }.forEach { candidate ->
            val result = lookup.read(candidate.messageId)
            val document = result.getOrNull()
            if (document != null && isValid(document, expectedKey)) {
                if (newest == null || document.internalDate > requireNotNull(newest).internalDate) {
                    newest = document
                }
            } else {
                result.exceptionOrNull()?.takeUnless(::canSkip)?.let {
                    return Result.failure(it)
                }
            }
        }

        return Result.success(newest)
    }

    private fun isValid(document: GmailArchiveDocument, expectedKey: String): Boolean {
        if (!document.accountEmail.equals(accountEmail, ignoreCase = true)) return false
        val parsedKey = runCatching {
            ArchiveConversationIdentity.key(
                document.conversation.address,
                document.accountEmail
            )
        }.getOrNull() ?: return false
        if (parsedKey != expectedKey) return false
        return document.conversationKeyHeader == null ||
            document.conversationKeyHeader == expectedKey
    }

    private fun canSkip(error: Throwable): Boolean =
        error is ArchiveMessageNotFoundException ||
            error is InvalidArchiveSnapshotException ||
            error is GmailOperationException && error.failure.httpStatusCode == 404
}
