package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode

@Entity(
    tableName = "account_settings",
    foreignKeys = [
        ForeignKey(
            entity = AccountProfileEntity::class,
            parentColumns = ["profile_id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AccountSettingsEntity(

    @PrimaryKey
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "backup_enabled")
    val backupEnabled: Boolean = true,

    @ColumnInfo(name = "gmail_enabled")
    val gmailEnabled: Boolean = true,

    @ColumnInfo(name = "drive_enabled")
    val driveEnabled: Boolean = false,

    @ColumnInfo(name = "include_contact_names")
    val includeContactNames: Boolean = true,

    @ColumnInfo(name = "backup_mode")
    val backupMode: String = GmailBackupMode.MIRROR.name,

    @ColumnInfo(name = "backup_label")
    val backupLabel: String = "SMS",

    @ColumnInfo(name = "scheduled_backup_enabled")
    val scheduledBackupEnabled: Boolean = false,

    @ColumnInfo(name = "encryption_enabled")
    val encryptionEnabled: Boolean = false
)
