package io.github.isht1008.opensmsbackup.account.data

import io.github.isht1008.opensmsbackup.database.AccountSettingsEntity

object GmailBackupPolicyMetadata {
    fun changed(
        settings: AccountSettingsEntity,
        newMode: GmailBackupMode,
        changedAt: Long
    ): AccountSettingsEntity {
        val currentMode = GmailBackupMode.fromStorage(settings.backupMode)
        if (currentMode == newMode) return settings
        return settings.copy(
            backupMode = newMode.name,
            previousPolicy = currentMode.name,
            policyChangedAt = changedAt
        )
    }
}
