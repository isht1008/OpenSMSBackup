package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "mirror_reconciliation_runs",
    primaryKeys = ["run_id"],
    foreignKeys = [ForeignKey(
        entity = AccountProfileEntity::class,
        parentColumns = ["profile_id"],
        childColumns = ["profile_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("profile_id"),
        Index(value = ["profile_id", "device_id", "status"]),
        Index("created_at")
    ]
)
data class MirrorReconciliationRunEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "account_identity") val accountIdentity: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_label_id") val deviceLabelId: String,
    @ColumnInfo(name = "expected_policy") val expectedPolicy: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "local_dataset_fingerprint") val localDatasetFingerprint: String,
    @ColumnInfo(name = "remote_index_fingerprint") val remoteIndexFingerprint: String,
    @ColumnInfo(name = "local_scan_complete") val localScanComplete: Boolean,
    @ColumnInfo(name = "local_conversations") val localConversations: Int,
    @ColumnInfo(name = "owned_remote_conversations") val ownedRemoteConversations: Int,
    @ColumnInfo(name = "unchanged_count") val unchangedCount: Int,
    @ColumnInfo(name = "upload_new_count") val uploadNewCount: Int,
    @ColumnInfo(name = "replace_changed_count") val replaceChangedCount: Int,
    @ColumnInfo(name = "trash_remote_only_count") val trashRemoteOnlyCount: Int,
    @ColumnInfo(name = "recover_cache_count") val recoverCacheCount: Int,
    @ColumnInfo(name = "conflict_count") val conflictCount: Int,
    @ColumnInfo(name = "foreign_ignored_count") val foreignIgnoredCount: Int,
    @ColumnInfo(name = "failed_count") val failedCount: Int,
    @ColumnInfo(name = "estimated_reads") val estimatedReads: Int,
    @ColumnInfo(name = "estimated_uploads") val estimatedUploads: Int,
    @ColumnInfo(name = "estimated_trash_moves") val estimatedTrashMoves: Int,
    @ColumnInfo(name = "estimated_duration_millis") val estimatedDurationMillis: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Long? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "terminal_reason") val terminalReason: String? = null
)

@Entity(
    tableName = "mirror_reconciliation_items",
    primaryKeys = ["run_id", "item_id"],
    foreignKeys = [ForeignKey(
        entity = MirrorReconciliationRunEntity::class,
        parentColumns = ["run_id"],
        childColumns = ["run_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("run_id"),
        Index(value = ["run_id", "action", "state"]),
        Index(value = ["profile_id", "device_id"]),
        Index(value = ["profile_id", "android_thread_id"])
    ]
)
data class MirrorReconciliationItemEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "account_identity") val accountIdentity: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "android_thread_id") val androidThreadId: Long? = null,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "expected_local_source_hash") val expectedLocalSourceHash: String? = null,
    @ColumnInfo(name = "expected_remote_snapshot_hash") val expectedRemoteSnapshotHash: String? = null,
    @ColumnInfo(name = "prior_gmail_message_id") val priorGmailMessageId: String? = null,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "resulting_gmail_message_id") val resultingGmailMessageId: String? = null,
    @ColumnInfo(name = "warning_category") val warningCategory: String? = null,
    @ColumnInfo(name = "failure_category") val failureCategory: String? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "old_target_profile_id") val oldTargetProfileId: String? = null,
    @ColumnInfo(name = "old_target_account_identity") val oldTargetAccountIdentity: String? = null,
    @ColumnInfo(name = "old_target_device_id") val oldTargetDeviceId: String? = null,
    @ColumnInfo(name = "old_target_device_label_id") val oldTargetDeviceLabelId: String? = null,
    @ColumnInfo(name = "old_target_android_thread_id") val oldTargetAndroidThreadId: Long? = null,
    @ColumnInfo(name = "old_target_gmail_message_id") val oldTargetGmailMessageId: String? = null,
    @ColumnInfo(name = "old_target_snapshot_hash") val oldTargetSnapshotHash: String? = null,
    @ColumnInfo(name = "old_target_conversation_key_header") val oldTargetConversationKeyHeader: String? = null,
    @ColumnInfo(name = "old_target_identity_version_header") val oldTargetIdentityVersionHeader: String? = null,
    @ColumnInfo(name = "old_target_format_version_header") val oldTargetFormatVersionHeader: String? = null,
    @ColumnInfo(name = "old_target_proof_version") val oldTargetProofVersion: String? = null
)
