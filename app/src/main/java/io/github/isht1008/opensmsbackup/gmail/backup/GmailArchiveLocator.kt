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
    val conversation: SmsConversationSnapshot,
    val deviceIdHeader: String? = null,
    val identityVersionHeader: String? = null,
    val labelIds: Set<String> = emptySet(),
    val defaultRegionHeader: String? = null
)

interface GmailArchiveLookup {
    suspend fun read(messageId: String): Result<GmailArchiveDocument>
    suspend fun search(
        conversationKey: String,
        deviceLabelId: String
    ): Result<List<GmailArchiveCandidate>>
}

class ArchiveMessageNotFoundException : Exception("Gmail archive message was not found.")
class InvalidArchiveSnapshotException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class GmailArchiveLocator(
    private val lookup: GmailArchiveLookup,
    private val accountEmail: String,
    private val deviceId: String,
    private val deviceLabelId: String,
    private val defaultRegion: String = "US"
) {
    suspend fun locate(
        conversation: SmsConversationSnapshot,
        cachedMessageId: String?
    ): Result<GmailArchiveDocument?> {
        val expectedV3Key = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
            conversation.address,
            accountEmail,
            deviceId,
            defaultRegion
        )
        val expectedV2Key = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
            conversation.address,
            accountEmail,
            deviceId
        )

        cachedMessageId?.takeIf { it.isNotBlank() }?.let { messageId ->
            val cached = lookup.read(messageId)
            cached.getOrNull()?.takeIf {
                isValid(it, conversation, expectedV3Key, expectedV2Key, allowLegacy = true)
            }?.let {
                return Result.success(it)
            }
        }

        val v3Candidates = lookup.search(expectedV3Key, deviceLabelId).getOrElse {
            return Result.failure(it)
        }
        val v2Candidates = lookup.search(expectedV2Key, deviceLabelId).getOrElse {
            return Result.failure(it)
        }
        var newest: GmailArchiveDocument? = null

        (v3Candidates + v2Candidates).distinctBy { it.messageId }.forEach { candidate ->
            val result = lookup.read(candidate.messageId)
            val document = result.getOrNull()
            if (document != null && isValid(
                    document, conversation, expectedV3Key, expectedV2Key, allowLegacy = false
                )) {
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

    private fun isValid(
        document: GmailArchiveDocument,
        conversation: SmsConversationSnapshot,
        expectedV3Key: String,
        expectedV2Key: String,
        allowLegacy: Boolean
    ): Boolean {
        if (document.deviceIdHeader == null) {
            if (!document.accountEmail.equals(accountEmail, ignoreCase = true)) return false
            if (!allowLegacy) return false
            val legacyKey = ArchiveConversationIdentity.key(
                document.conversation.address,
                document.accountEmail
            )
            return document.conversationKeyHeader == null ||
                document.conversationKeyHeader == legacyKey
        }
        return DeviceSnapshotOwnership.matchesV2(
            document,
            accountEmail,
            deviceId,
            deviceLabelId,
            conversation,
            defaultRegion
        ) && document.conversationKeyHeader in setOf(expectedV3Key, expectedV2Key)
    }

    private fun canSkip(error: Throwable): Boolean =
        error is ArchiveMessageNotFoundException ||
            error is InvalidArchiveSnapshotException ||
            error is GmailOperationException && error.failure.httpStatusCode == 404
}
