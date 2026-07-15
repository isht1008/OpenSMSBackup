package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_accounts",
    indices = [
        Index(
            value = ["account_email"],
            unique = true
        )
    ]
)
data class BackupAccountEntity(

    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "account_email")
    val accountEmail: String,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,

    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String? = null,

    @ColumnInfo(name = "drive_revision_id")
    val driveRevisionId: String? = null,

    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long = 0L,

    @ColumnInfo(name = "last_backup_time")
    val lastBackupTime: Long = 0L,

    @ColumnInfo(name = "last_restore_time")
    val lastRestoreTime: Long = 0L,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_time")
    val createdTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "backup_enabled")
    val backupEnabled: Boolean = true,

    @ColumnInfo(name = "backup_label")
    val backupLabel: String = "SMS",
)