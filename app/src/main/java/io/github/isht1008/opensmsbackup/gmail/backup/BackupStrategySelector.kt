package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode

class BackupStrategySelector(
    private val mirrorStrategy: MirrorBackupStrategy,
    private val archiveAppendStrategy: ArchiveAppendBackupStrategy
) {
    fun select(mode: GmailBackupMode): BackupStrategy =
        when (mode) {
            GmailBackupMode.MIRROR -> mirrorStrategy
            GmailBackupMode.ARCHIVE_APPEND_ONLY -> archiveAppendStrategy
        }
}
