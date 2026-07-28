package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity

data class ArchiveConversationCheckpoint(
    val conversation: SmsConversationSnapshot,
    val localSourceHash: String,
    val existingSnapshot: ConversationSnapshotEntity?
) {
    val hasValidCachedGmailReference: Boolean
        get() = existingSnapshot?.gmailMessageId?.isNotBlank() == true
}

data class ArchiveCheckpointClassification(
    val locallyUnchanged: List<ArchiveConversationCheckpoint>,
    val requiringComparison: List<ArchiveConversationCheckpoint>,
    val requiringLocalHashUpgrade: List<ArchiveConversationCheckpoint> = emptyList(),
    val locallyBootstrappable: List<ArchiveConversationCheckpoint> = emptyList(),
    val uninitialized: Int = 0,
    val deviceMismatched: Int = 0,
    val hashMismatched: Int = 0,
    val missingCachedGmailId: Int = 0,
    val bootstrapRejectedHash: Int = 0,
    val bootstrapRejectedMissingId: Int = 0,
    val bootstrapRejectedNoFullProof: Int = 0,
    val bootstrapRejectedDevice: Int = 0
)

object ArchiveLocalCheckpointClassifier {
    fun isFastPathEligible(
        scope: GmailBackupScope,
        mode: GmailBackupMode
    ): Boolean =
        scope == GmailBackupScope.INCREMENTAL &&
            mode == GmailBackupMode.ARCHIVE_APPEND_ONLY

    fun classify(
        conversations: List<SmsConversationSnapshot>,
        snapshotsByThreadId: Map<Long, ConversationSnapshotEntity>,
        accountId: String,
        deviceId: String
    ): ArchiveCheckpointClassification = classify(
        conversations = conversations,
        snapshotsByThreadId = snapshotsByThreadId,
        accountId = accountId,
        accountEmail = snapshotsByThreadId.values.firstOrNull()?.accountEmail.orEmpty(),
        deviceId = deviceId,
        completedFullBackupTime = 0L
    )

    fun classify(
        conversations: List<SmsConversationSnapshot>,
        snapshotsByThreadId: Map<Long, ConversationSnapshotEntity>,
        accountId: String,
        accountEmail: String,
        deviceId: String,
        completedFullBackupTime: Long,
        installationContextSafe: Boolean = true
    ): ArchiveCheckpointClassification {
        val unchanged = mutableListOf<ArchiveConversationCheckpoint>()
        val compare = mutableListOf<ArchiveConversationCheckpoint>()
        val upgrades = mutableListOf<ArchiveConversationCheckpoint>()
        val bootstrappable = mutableListOf<ArchiveConversationCheckpoint>()
        var uninitialized = 0
        var deviceMismatched = 0
        var hashMismatched = 0
        var missingCachedGmailId = 0
        var rejectedHash = 0
        var rejectedMissingId = 0
        var rejectedNoFullProof = 0
        var rejectedDevice = 0
        conversations.forEach { conversation ->
            val existing = snapshotsByThreadId[conversation.threadId]
                ?.takeIf { it.accountId == accountId }
            val hash = LocalConversationSourceHashGenerator.generate(conversation)
            val item = ArchiveConversationCheckpoint(conversation, hash, existing)
            val currentHashMatches = existing?.localSourceHash == hash
            val legacyHashMatches =
                existing?.localSourceHash ==
                    LocalConversationSourceHashGenerator.generateLegacyV1(conversation)
            val accountMatches = existing?.accountEmail?.trim()
                ?.equals(accountEmail.trim(), ignoreCase = true) == true
            if (
                (currentHashMatches || legacyHashMatches) &&
                existing.localSourceDeviceId == deviceId &&
                accountMatches &&
                item.hasValidCachedGmailReference
            ) {
                unchanged += item
                if (!currentHashMatches && legacyHashMatches) upgrades += item
            } else if (existing != null && existing.localSourceHash == null) {
                val hasId = item.hasValidCachedGmailReference
                val hasFullProof = completedFullBackupTime > 0L &&
                    existing.backupTime <= completedFullBackupTime
                val deviceSafe = installationContextSafe && accountMatches
                val snapshotMatches = existing.snapshotHash.isNotBlank() &&
                    ConversationSnapshotHashGenerator.generate(conversation) == existing.snapshotHash
                if (hasId && hasFullProof && deviceSafe && snapshotMatches) {
                    unchanged += item
                    bootstrappable += item
                } else {
                    compare += item
                    uninitialized++
                    if (!hasId) rejectedMissingId++
                    if (!hasFullProof) rejectedNoFullProof++
                    if (!deviceSafe) rejectedDevice++
                    if (!snapshotMatches) rejectedHash++
                }
            } else {
                compare += item
                when {
                    existing?.localSourceHash == null -> uninitialized++
                    existing.localSourceDeviceId != deviceId -> deviceMismatched++
                    existing.localSourceHash != hash -> hashMismatched++
                    !item.hasValidCachedGmailReference -> missingCachedGmailId++
                }
            }
        }
        return ArchiveCheckpointClassification(
            locallyUnchanged = unchanged,
            requiringComparison = compare,
            requiringLocalHashUpgrade = upgrades,
            locallyBootstrappable = bootstrappable,
            uninitialized = uninitialized,
            deviceMismatched = deviceMismatched,
            hashMismatched = hashMismatched,
            missingCachedGmailId = missingCachedGmailId,
            bootstrapRejectedHash = rejectedHash,
            bootstrapRejectedMissingId = rejectedMissingId,
            bootstrapRejectedNoFullProof = rejectedNoFullProof,
            bootstrapRejectedDevice = rejectedDevice
        )
    }
}
