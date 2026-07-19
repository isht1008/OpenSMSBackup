package io.github.isht1008.opensmsbackup.restore

import com.google.api.services.gmail.Gmail
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveDocument
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchivedConversationReader
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy

class GmailRestoreSourceRepository(
    private val gmail: Gmail,
    private val profileId: String,
    private val accountEmail: String,
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy()
) : RestoreSourceRepository {
    private val reader = GmailArchivedConversationReader(gmail, profileId, retryPolicy)

    override suspend fun discoverDevices(): Result<List<RestoreSourceDevice>> = runCatching {
        val labels = retryPolicy.execute("restore_list_device_labels", profileId) {
            gmail.users().labels().list("me").execute()
        }.labels.orEmpty()
        labels.mapNotNull { label ->
            val name = label.name ?: return@mapNotNull null
            if (!name.startsWith(DEVICE_PREFIX) || !name.endsWith(DEVICE_SUFFIX)) return@mapNotNull null
            val displayName = name.removePrefix(DEVICE_PREFIX).removeSuffix(DEVICE_SUFFIX)
                .trim('/').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val labelId = label.id ?: return@mapNotNull null
            val messages = retryPolicy.execute("restore_probe_device", profileId) {
                gmail.users().messages().list("me").setLabelIds(listOf(labelId))
                    .setMaxResults(DEVICE_PROBE_LIMIT).execute()
            }.messages.orEmpty()
            var document: GmailArchiveDocument? = null
            for (message in messages) {
                val candidate = message.id?.let { reader.read(it).getOrNull() } ?: continue
                if (RestoreCatalog.sourceDevice(candidate, accountEmail, labelId, displayName) != null) {
                    document = candidate
                    break
                }
            }
            val validDocument = document ?: return@mapNotNull null
            requireNotNull(RestoreCatalog.sourceDevice(validDocument, accountEmail, labelId, displayName))
        }.distinctBy { it.deviceId }.sortedBy { it.displayName.lowercase() }
    }

    override suspend fun loadConversations(device: RestoreSourceDevice): Result<List<RestoreConversation>> = runCatching {
        val candidates = retryPolicy.execute("restore_list_snapshots", profileId) {
            gmail.users().messages().list("me")
                .setLabelIds(listOf(device.labelId)).setMaxResults(MAX_MESSAGES).execute()
        }.messages.orEmpty()
        val documents = ArrayList<GmailArchiveDocument>(candidates.size)
        for (candidate in candidates) {
            val id = candidate.id ?: continue
            reader.read(id).getOrNull()?.let(documents::add)
        }
        RestoreCatalog.newestValid(documents, accountEmail, device.deviceId, device.labelId)
    }

    private companion object {
        const val DEVICE_PREFIX = "SMS/Devices/"
        const val DEVICE_SUFFIX = "/Conversations"
        const val MAX_MESSAGES = 500L
        const val DEVICE_PROBE_LIMIT = 10L
    }
}
