package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_messages",
    indices = [
        Index(
            value = ["account_id", "fingerprint"],
            unique = true
        ),
        Index(
            value = ["account_id", "sms_id"]
        ),
        Index(
            value = ["thread_id"]
        )
    ]
)
data class BackupMessageEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "account_email")
    val accountEmail: String,

    @ColumnInfo(name = "sms_id")
    val smsId: Long,

    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "message_date")
    val messageDate: Long,

    @ColumnInfo(name = "message_type")
    val messageType: Int,

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "gmail_message_id")
    val gmailMessageId: String? = null,

    @ColumnInfo(name = "gmail_thread_id")
    val gmailThreadId: String? = null,

    @ColumnInfo(name = "backup_time")
    val backupTime: Long = System.currentTimeMillis()
)