package io.github.isht1008.opensmsbackup.verification

import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity

fun BackupVerificationResult.toEntity() = BackupVerificationEntity(
    profileId = profileId, accountEmail = accountEmail, deviceId = deviceId,
    deviceName = deviceDisplayName, mode = mode.name, startedAt = startedAt,
    completedAt = completedAt, status = status.name,
    localMessageCount = localMessageCount, localConversationCount = localConversationCount,
    archivedMessageCount = archivedMessageCount, archivedConversationCount = archivedConversationCount,
    matchedMessageCount = matchedMessageCount, missingMessageCount = missingMessageCount,
    unexpectedArchivedMessageCount = unexpectedArchivedMessageCount,
    duplicateFingerprintCount = duplicateFingerprintCount,
    unreadableArchiveCount = unreadableArchiveCount, verificationPercent = verificationPercent,
    shortSummary = when (status) {
        BackupVerificationStatus.VERIFIED -> "All $localMessageCount local messages were found in Gmail."
        BackupVerificationStatus.PARTIALLY_VERIFIED -> "Local messages were found, but the archive has differences or incomplete checks."
        BackupVerificationStatus.FAILED -> "$missingMessageCount local messages were not found in the verified Gmail archive."
        BackupVerificationStatus.NOT_AVAILABLE -> "No valid archive was available for this account and device."
        BackupVerificationStatus.CANCELLED -> "Verification was cancelled before completion."
    }
)
