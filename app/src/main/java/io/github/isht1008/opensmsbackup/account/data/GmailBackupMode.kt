package io.github.isht1008.opensmsbackup.account.data

enum class GmailBackupMode {
    MIRROR,
    ARCHIVE_APPEND_ONLY;

    companion object {
        fun fromStorage(value: String?): GmailBackupMode =
            entries.firstOrNull { it.name == value } ?: MIRROR
    }
}
