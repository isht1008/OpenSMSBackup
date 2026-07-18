package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_snapshots",
    indices = [
        Index(
            value = [
                "account_id",
                "android_thread_id"
            ],
            unique = true
        ),
        Index(
            value = ["account_email"]
        ),
        Index(
            value = ["android_thread_id"]
        )
    ]
)
data class ConversationSnapshotEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "account_email")
    val accountEmail: String,

    @ColumnInfo(name = "android_thread_id")
    val androidThreadId: Long,

    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "contact_name")
    val contactName: String? = null,

    @ColumnInfo(name = "message_count")
    val messageCount: Int,

    @ColumnInfo(name = "snapshot_hash")
    val snapshotHash: String,

    @ColumnInfo(name = "gmail_message_id")
    val gmailMessageId: String,

    @ColumnInfo(name = "gmail_thread_id")
    val gmailThreadId: String? = null,

    @ColumnInfo(name = "first_message_date")
    val firstMessageDate: Long,

    @ColumnInfo(name = "last_message_date")
    val lastMessageDate: Long,

    @ColumnInfo(name = "backup_time")
    val backupTime: Long = System.currentTimeMillis()
)
