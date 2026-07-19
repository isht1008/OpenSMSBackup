package io.github.isht1008.opensmsbackup.verification

import com.google.api.services.gmail.Gmail
import io.github.isht1008.opensmsbackup.gmail.backup.ArchiveConversationIdentity
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveDocument
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchivedConversationReader
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class VerificationMessagePage(val messageIds: List<String>, val nextPageToken: String?)

interface VerificationGmailGateway {
    suspend fun listPage(labelId: String, pageToken: String?): Result<VerificationMessagePage>
    suspend fun read(messageId: String): Result<GmailArchiveDocument>
}

class AndroidVerificationGmailGateway(
    private val gmail: Gmail,
    private val profileId: String,
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy()
) : VerificationGmailGateway {
    private val reader = GmailArchivedConversationReader(gmail, profileId, retryPolicy)
    override suspend fun listPage(labelId: String, pageToken: String?): Result<VerificationMessagePage> =
        try {
            val response = retryPolicy.execute("verification_list_archives", profileId) {
                withContext(Dispatchers.IO) {
                    gmail.users().messages().list("me").setLabelIds(listOf(labelId))
                        .setPageToken(pageToken).setMaxResults(PAGE_SIZE).execute()
                }
            }
            Result.success(VerificationMessagePage(response.messages.orEmpty().mapNotNull { it.id }, response.nextPageToken))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    override suspend fun read(messageId: String) = reader.read(messageId)
    private companion object { const val PAGE_SIZE = 500L }
}

class GmailVerificationRepository(
    private val gateway: VerificationGmailGateway,
    private val deviceLabelId: String,
    private val maximumMessages: Int = 100_000,
    private val maximumPages: Int = 10_000
) : VerificationArchiveRepository {
    override suspend fun loadArchive(request: BackupVerificationRequest): Result<VerificationArchiveSnapshot> = try {
        val newestByConversation = HashMap<String, GmailArchiveDocument>()
        val issueCounts = LinkedHashMap<BackupVerificationIssueType, Int>()
        val seenPageTokens = HashSet<String>()
        var unreadable = 0
        var pageToken: String? = null
        var pages = 0
        var loaded = 0
        var complete = true
        do {
            currentCoroutineContext().ensureActive()
            if (pages++ >= maximumPages || pageToken?.let { !seenPageTokens.add(it) } == true) {
                complete = false
                issueCounts.increment(BackupVerificationIssueType.SAFETY_LIMIT_REACHED)
                break
            }
            val page = gateway.listPage(deviceLabelId, pageToken).getOrThrow()
            for (id in page.messageIds) {
                if (loaded >= maximumMessages) {
                    complete = false
                    issueCounts.increment(BackupVerificationIssueType.SAFETY_LIMIT_REACHED)
                    break
                }
                loaded++
                gateway.read(id).onSuccess { document ->
                    validate(document, request)?.let { issueCounts.increment(it.type) } ?: run {
                        val key = requireNotNull(document.conversationKeyHeader)
                        val current = newestByConversation[key]
                        if (current == null || document.internalDate > current.internalDate) {
                            newestByConversation[key] = document
                        }
                    }
                }.onFailure {
                    unreadable++
                }
            }
            if (!complete) break
            pageToken = page.nextPageToken
        } while (pageToken != null)

        val newest = newestByConversation.values
        val issues = issueCounts.map { (type, count) -> BackupVerificationIssue(type, count) }
        Result.success(VerificationArchiveSnapshot(
            messages = newest.flatMap { it.conversation.messages },
            conversationCount = newest.size,
            unreadableArchiveCount = unreadable,
            issues = issues,
            complete = complete
        ))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun validate(
        document: GmailArchiveDocument,
        request: BackupVerificationRequest
    ): BackupVerificationIssue? {
        if (!document.accountEmail.equals(request.accountEmail, true)) {
            return BackupVerificationIssue(BackupVerificationIssueType.WRONG_ACCOUNT)
        }
        if (document.deviceIdHeader != request.deviceId || deviceLabelId !in document.labelIds) {
            return BackupVerificationIssue(BackupVerificationIssueType.WRONG_DEVICE)
        }
        val version = when (document.identityVersionHeader) {
            "2" -> ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS
            "3" -> ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS
            else -> return BackupVerificationIssue(BackupVerificationIssueType.UNSUPPORTED_ARCHIVE_VERSION)
        }
        val expected = ArchiveConversationIdentity.key(
            version, document.conversation.address, request.accountEmail, request.deviceId,
            document.defaultRegionHeader ?: request.defaultRegion
        )
        if (document.conversationKeyHeader != expected) {
            return BackupVerificationIssue(BackupVerificationIssueType.INVALID_CONVERSATION_IDENTITY)
        }
        return null
    }

    private fun MutableMap<BackupVerificationIssueType, Int>.increment(
        type: BackupVerificationIssueType
    ) {
        this[type] = (this[type] ?: 0) + 1
    }
}
