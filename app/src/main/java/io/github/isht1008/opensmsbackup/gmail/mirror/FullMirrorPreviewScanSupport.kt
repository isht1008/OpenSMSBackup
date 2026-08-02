package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.database.MirrorPreviewRemoteItemEntity
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchiveMetadataReference
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.LocalConversationSourceHashGenerator
import io.github.isht1008.opensmsbackup.sms.NormalizedSmsAddress
import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object MirrorPreviewAccountBinding {
    fun fingerprint(profileId: String, accountEmail: String): String = digest(
        "$profileId\u0000${accountEmail.trim().lowercase(Locale.ROOT)}"
    )

    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

object FullMirrorLocalScalarFactory {
    fun create(
        profileId: String,
        accountIdentity: String,
        deviceId: String,
        defaultRegion: String,
        conversation: SmsConversationSnapshot
    ): FullMirrorLocalScalar = FullMirrorLocalScalar(
        androidThreadId = conversation.threadId,
        snapshotHash = ConversationSnapshotHashGenerator.generate(conversation),
        localSourceHash = LocalConversationSourceHashGenerator.generate(conversation),
        messageCount = conversation.messageCount,
        diagnosticReason = addressReason(conversation.address, defaultRegion)
    )

    private fun addressReason(address: String?, region: String): FullMirrorFailureCategory? {
        if (address == null) return FullMirrorFailureCategory.MISSING_ADDRESS
        if (address.isBlank()) return FullMirrorFailureCategory.EMPTY_ADDRESS
        if (address.contains(',') || address.contains(';')) {
            return FullMirrorFailureCategory.GROUP_MULTI_RECIPIENT
        }
        if (address.contains('@')) return FullMirrorFailureCategory.EMAIL_LIKE_SENDER
        return when (SmsAddressNormalizer().normalize(address, region)) {
            is NormalizedSmsAddress.PhoneNumber -> null
            is NormalizedSmsAddress.ShortCode -> FullMirrorFailureCategory.SHORT_CODE
            is NormalizedSmsAddress.SenderId -> FullMirrorFailureCategory.ALPHANUMERIC_SENDER
            is NormalizedSmsAddress.Unknown -> FullMirrorFailureCategory.PHONE_NORMALIZATION_FAILURE
        }
    }
}

data class MirrorMetadataDecision(
    val accepted: Boolean,
    val reason: FullMirrorFailureCategory,
    val item: MirrorPreviewRemoteItemEntity
)

object MirrorPreviewMetadataValidator {
    fun validate(
        scanId: String,
        generation: Int,
        reference: GmailArchiveMetadataReference,
        binding: FullMirrorBinding
    ): MirrorMetadataDecision {
        val accountValid = reference.accountHeader.equals(binding.accountIdentity, true)
        val deviceValid = reference.deviceIdHeader == binding.deviceId
        val labelValid = binding.deviceLabelId in reference.labelIds
        val key = reference.conversationKeyHeader.orEmpty()
        val reason = when {
            reference.messageId.isBlank() || reference.accountHeader.isNullOrBlank() ||
                reference.deviceIdHeader.isNullOrBlank() || key.isBlank() ->
                FullMirrorFailureCategory.MISSING_OWNERSHIP_HEADER
            !labelValid -> FullMirrorFailureCategory.LABEL_MISMATCH
            !accountValid -> FullMirrorFailureCategory.ACCOUNT_MISMATCH
            !deviceValid -> FullMirrorFailureCategory.DEVICE_MISMATCH
            reference.formatVersionHeader != "3" -> FullMirrorFailureCategory.FORMAT_VERSION_MISMATCH
            reference.identityVersionHeader !in setOf("2", "3", MirrorConversationIdentity.WIRE_NAME) ->
                FullMirrorFailureCategory.UNSUPPORTED_IDENTITY_VERSION
            else -> FullMirrorFailureCategory.NONE
        }
        val accepted = reason == FullMirrorFailureCategory.NONE
        return MirrorMetadataDecision(
            accepted,
            reason,
            MirrorPreviewRemoteItemEntity(
                scanId = scanId,
                gmailMessageId = reference.messageId,
                seenGeneration = generation,
                itemState = if (accepted) "METADATA" else "IGNORED",
                conversationKey = key,
                androidThreadId = reference.androidThreadIdHeader,
                snapshotHash = reference.snapshotHashHeader,
                identityVersion = reference.identityVersionHeader,
                formatVersion = reference.formatVersionHeader,
                accountBindingValid = accountValid,
                deviceBindingValid = deviceValid,
                labelBindingValid = labelValid,
                identityCurrent = reference.identityVersionHeader == MirrorConversationIdentity.WIRE_NAME,
                reason = reason.takeUnless { it == FullMirrorFailureCategory.NONE }?.name
            )
        )
    }
}

object MirrorPreviewCacheFastPath {
    fun trustUnchanged(
        item: MirrorPreviewRemoteItemEntity,
        binding: FullMirrorBinding,
        local: FullMirrorLocalScalar?,
        cache: ConversationSnapshotEntity?,
        duplicateCount: Int
    ): Boolean {
        val threadId = item.androidThreadId ?: return false
        if (duplicateCount != 1 || local == null || cache == null || threadId <= 0L) return false
        if (!item.accountBindingValid || !item.deviceBindingValid || !item.labelBindingValid) return false
        if (item.identityVersion != MirrorConversationIdentity.WIRE_NAME || item.formatVersion != "3") return false
        if (local.androidThreadId != threadId || cache.androidThreadId != threadId) return false
        if (cache.profileId != binding.profileId ||
            !cache.accountId.equals(binding.accountIdentity, true) ||
            cache.gmailMessageId != item.gmailMessageId
        ) return false
        val expectedKey = MirrorConversationIdentity.key(
            binding.profileId,
            binding.accountIdentity,
            binding.deviceId,
            threadId
        )
        if (item.conversationKey != expectedKey) return false
        val metadataHash = item.snapshotHash ?: return false
        return metadataHash == cache.snapshotHash && metadataHash == local.snapshotHash
    }
}

object MirrorPreviewNetworkCircuit {
    const val TERMINAL_FAILURES_TO_PAUSE = 1
    const val BASE_DELAY_MILLIS = 30_000L
    const val MAX_DELAY_MILLIS = 15L * 60L * 1_000L

    fun shouldPause(consecutiveTerminalFailures: Int) =
        consecutiveTerminalFailures >= TERMINAL_FAILURES_TO_PAUSE

    fun retryDelay(retryCount: Int, retryAfterMillis: Long?): Long {
        val boundedExponential = (BASE_DELAY_MILLIS shl retryCount.coerceIn(0, 5))
            .coerceAtMost(MAX_DELAY_MILLIS)
        return maxOf(boundedExponential, retryAfterMillis ?: 0L)
    }

    fun mayAttempt(retryAt: Long?, now: Long): Boolean =
        retryAt == null || retryAt <= now

    fun retryDeadline(now: Long, delayMillis: Long, expiresAt: Long): Long {
        val untilExpiry = (expiresAt - now).coerceAtLeast(0L)
        val boundedByExpiry = delayMillis.coerceIn(0L, untilExpiry)
        return now + boundedByExpiry
    }
}

data class MirrorPreviewResumeSelection(
    val promoted: List<MirrorPreviewRemoteItemEntity>,
    val metadataIds: List<String>
)

object MirrorPreviewGenerationResume {
    fun select(
        pageIds: List<String>,
        currentGenerationIds: Set<String>,
        persistedItems: List<MirrorPreviewRemoteItemEntity>,
        generation: Int
    ): MirrorPreviewResumeSelection {
        val previousById = persistedItems.associateBy { it.gmailMessageId }
        val promoted = mutableListOf<MirrorPreviewRemoteItemEntity>()
        val metadataIds = mutableListOf<String>()
        pageIds.asSequence().filter(String::isNotBlank).distinct().forEach { id ->
            if (id in currentGenerationIds) return@forEach
            val previous = previousById[id]
            if (previous == null) metadataIds += id
            else promoted += previous.copy(seenGeneration = generation)
        }
        return MirrorPreviewResumeSelection(promoted, metadataIds)
    }
}
