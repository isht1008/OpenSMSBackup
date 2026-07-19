package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "backup_verifications", indices = [Index("profile_id"), Index("completed_at")])
data class BackupVerificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "account_email") val accountEmail: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_name") val deviceName: String,
    val mode: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    val status: String,
    @ColumnInfo(name = "local_message_count") val localMessageCount: Int,
    @ColumnInfo(name = "local_conversation_count") val localConversationCount: Int,
    @ColumnInfo(name = "archived_message_count") val archivedMessageCount: Int,
    @ColumnInfo(name = "archived_conversation_count") val archivedConversationCount: Int,
    @ColumnInfo(name = "matched_message_count") val matchedMessageCount: Int,
    @ColumnInfo(name = "missing_message_count") val missingMessageCount: Int,
    @ColumnInfo(name = "unexpected_archived_message_count") val unexpectedArchivedMessageCount: Int,
    @ColumnInfo(name = "duplicate_fingerprint_count") val duplicateFingerprintCount: Int,
    @ColumnInfo(name = "unreadable_archive_count") val unreadableArchiveCount: Int,
    @ColumnInfo(name = "verification_percent") val verificationPercent: Double,
    @ColumnInfo(name = "short_summary") val shortSummary: String
)
