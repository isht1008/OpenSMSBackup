package io.github.isht1008.opensmsbackup.restore

import io.github.isht1008.opensmsbackup.gmail.backup.ArchiveConversationIdentity
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveDocument

interface RestoreSourceRepository {
    suspend fun discoverDevices(): Result<List<RestoreSourceDevice>>
    suspend fun loadConversations(device: RestoreSourceDevice): Result<List<RestoreConversation>>
}

object RestoreCatalog {
    fun sourceDevice(
        document: GmailArchiveDocument,
        expectedAccount: String,
        labelId: String,
        fallbackName: String
    ): RestoreSourceDevice? {
        if (!document.accountEmail.equals(expectedAccount, ignoreCase = true)) return null
        if (labelId !in document.labelIds) return null
        if (document.identityVersionHeader !in setOf("2", "3")) return null
        val deviceId = document.deviceIdHeader?.takeIf { it.isNotBlank() } ?: return null
        return RestoreSourceDevice(
            deviceId,
            document.deviceNameHeader?.takeIf { it.isNotBlank() } ?: fallbackName,
            labelId
        )
    }

    fun newestValid(
        documents: Iterable<GmailArchiveDocument>,
        expectedAccount: String,
        expectedDeviceId: String,
        expectedLabelId: String
    ): List<RestoreConversation> = documents.asSequence()
        .filter { it.accountEmail.equals(expectedAccount, ignoreCase = true) }
        .filter { it.deviceIdHeader == expectedDeviceId }
        .filter { expectedLabelId in it.labelIds }
        .mapNotNull { document ->
            val version = when (document.identityVersionHeader) {
                "2" -> ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS
                "3" -> ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS
                else -> return@mapNotNull null
            }
            val key = runCatching {
                ArchiveConversationIdentity.key(
                    version,
                    document.conversation.address,
                    document.accountEmail,
                    expectedDeviceId,
                    document.defaultRegionHeader ?: "US"
                )
            }.getOrNull() ?: return@mapNotNull null
            if (document.conversationKeyHeader != key) return@mapNotNull null
            RestoreConversation(key, document.conversation, document.internalDate)
        }
        .groupBy { it.key }
        .values.mapNotNull { candidates -> candidates.maxByOrNull { it.internalDate } }
        .sortedBy { it.snapshot.contactName ?: it.snapshot.address.orEmpty() }
}
