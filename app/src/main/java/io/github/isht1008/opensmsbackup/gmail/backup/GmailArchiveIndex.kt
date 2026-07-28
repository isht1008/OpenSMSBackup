package io.github.isht1008.opensmsbackup.gmail.backup

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class GmailArchiveMetadataReference(
    val messageId: String,
    val threadId: String?,
    val internalDate: Long,
    val labelIds: Set<String>,
    val accountHeader: String?,
    val deviceIdHeader: String?,
    val conversationKeyHeader: String?,
    val identityVersionHeader: String?,
    val formatVersionHeader: String?
)

data class GmailArchiveMessagePage(val messageIds: List<String>, val nextPageToken: String?)

interface GmailArchiveIndexSource {
    suspend fun listPage(deviceLabelId: String, pageToken: String?): GmailArchiveMessagePage
    suspend fun readMetadataPage(messageIds: List<String>): List<GmailArchiveMetadataReference>
}

data class GmailArchiveIndexedCandidate(
    val messageId: String,
    val threadId: String?,
    val internalDate: Long
)

class GmailArchiveIndex private constructor(
    private val v3: MutableMap<String, MutableList<GmailArchiveIndexedCandidate>> = mutableMapOf(),
    private val v2: MutableMap<String, MutableList<GmailArchiveIndexedCandidate>> = mutableMapOf()
) {
    val identityCount: Int get() = v3.size + v2.size

    fun candidates(v3Key: String, v2Key: String): List<GmailArchiveIndexedCandidate> =
        (v3[v3Key].orEmpty() + v2[v2Key].orEmpty()).distinctBy { it.messageId }

    fun recordUploaded(
        conversation: SmsConversationSnapshot,
        accountEmail: String,
        deviceId: String,
        defaultRegion: String,
        messageId: String,
        threadId: String?,
        internalDate: Long
    ) {
        val key = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
            conversation.address, accountEmail, deviceId, defaultRegion
        )
        add(v3, key, GmailArchiveIndexedCandidate(messageId, threadId, internalDate))
    }

    private fun accept(
        metadata: GmailArchiveMetadataReference,
        accountEmail: String,
        deviceId: String,
        deviceLabelId: String
    ): Boolean {
        if (metadata.messageId.isBlank() || deviceLabelId !in metadata.labelIds) return false
        if (!metadata.accountHeader.equals(accountEmail, ignoreCase = true)) return false
        if (metadata.deviceIdHeader != deviceId || metadata.formatVersionHeader != "3") return false
        val target = when (metadata.identityVersionHeader) {
            "3" -> v3
            "2" -> v2
            else -> return false
        }
        val key = metadata.conversationKeyHeader?.trim()?.takeIf(KEY_PATTERN::matches)
            ?: return false
        if (key !in target && identityCount >= MAX_IDENTITIES) {
            throw GmailArchiveIndexLimitException()
        }
        add(target, key, GmailArchiveIndexedCandidate(
            metadata.messageId, metadata.threadId, metadata.internalDate
        ))
        return true
    }

    private fun add(
        target: MutableMap<String, MutableList<GmailArchiveIndexedCandidate>>,
        key: String,
        candidate: GmailArchiveIndexedCandidate
    ) {
        val values = target.getOrPut(key) { mutableListOf() }
        if (values.any { it.messageId == candidate.messageId }) return
        values += candidate
        values.sortWith(compareByDescending<GmailArchiveIndexedCandidate> { it.internalDate }
            .thenBy { it.messageId })
        if (values.size > MAX_CANDIDATES_PER_IDENTITY) {
            values.subList(MAX_CANDIDATES_PER_IDENTITY, values.size).clear()
        }
    }

    companion object {
        const val MAX_IDENTITIES = 100_000
        const val MAX_CANDIDATES_PER_IDENTITY = 4
        private val KEY_PATTERN = Regex("[0-9a-f]{64}")

        suspend fun build(
            source: GmailArchiveIndexSource,
            accountEmail: String,
            deviceId: String,
            deviceLabelId: String,
            onProgress: suspend (scanned: Int, accepted: Int) -> Unit = { _, _ -> }
        ): GmailArchiveIndex {
            val index = GmailArchiveIndex()
            val seen = mutableSetOf<String>()
            var token: String? = null
            var scanned = 0
            var accepted = 0
            do {
                currentCoroutineContext().ensureActive()
                val page = source.listPage(deviceLabelId, token)
                val ids = page.messageIds.filter { it.isNotBlank() && seen.add(it) }
                val metadata = source.readMetadataPage(ids).associateBy { it.messageId }
                ids.forEach { id ->
                    currentCoroutineContext().ensureActive()
                    scanned++
                    metadata[id]?.let {
                        if (index.accept(it, accountEmail, deviceId, deviceLabelId)) accepted++
                    }
                    onProgress(scanned, accepted)
                }
                token = page.nextPageToken
            } while (token != null)
            return index
        }
    }
}

class GmailArchiveIndexLimitException :
    Exception("The Gmail Archive index exceeds the safe in-memory limit.")
