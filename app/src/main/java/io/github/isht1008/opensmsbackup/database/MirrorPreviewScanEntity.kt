package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "mirror_preview_scans",
    primaryKeys = ["scan_id"],
    foreignKeys = [ForeignKey(
        entity = AccountProfileEntity::class,
        parentColumns = ["profile_id"],
        childColumns = ["profile_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("profile_id"),
        Index(value = ["profile_id", "device_id", "lifecycle_state"]),
        Index("expires_at"),
        Index("published_run_id")
    ]
)
data class MirrorPreviewScanEntity(
    @ColumnInfo(name = "scan_id") val scanId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "account_fingerprint") val accountFingerprint: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_label_id") val deviceLabelId: String,
    @ColumnInfo(name = "expected_policy") val expectedPolicy: String,
    @ColumnInfo(name = "include_contact_names") val includeContactNames: Boolean,
    @ColumnInfo(name = "lifecycle_state") val lifecycleState: String,
    @ColumnInfo(name = "stage") val stage: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "local_dataset_fingerprint") val localDatasetFingerprint: String? = null,
    @ColumnInfo(name = "local_scan_complete") val localScanComplete: Boolean = false,
    @ColumnInfo(name = "local_count_consistent") val localCountConsistent: Boolean = false,
    @ColumnInfo(name = "local_failure_category") val localFailureCategory: String? = null,
    @ColumnInfo(name = "local_message_count") val localMessageCount: Int = 0,
    @ColumnInfo(name = "local_conversation_count") val localConversationCount: Int = 0,
    @ColumnInfo(name = "local_processed") val localProcessed: Int = 0,
    @ColumnInfo(name = "remote_generation") val remoteGeneration: Int = 1,
    @ColumnInfo(name = "remote_page_cursor") val remotePageCursor: String? = null,
    @ColumnInfo(name = "remote_discovery_complete") val remoteDiscoveryComplete: Boolean = false,
    @ColumnInfo(name = "remote_discovery_fingerprint") val remoteDiscoveryFingerprint: String? = null,
    @ColumnInfo(name = "remote_discovered") val remoteDiscovered: Int = 0,
    @ColumnInfo(name = "metadata_checked") val metadataChecked: Int = 0,
    @ColumnInfo(name = "full_reads_required") val fullReadsRequired: Int = 0,
    @ColumnInfo(name = "full_reads_completed") val fullReadsCompleted: Int = 0,
    @ColumnInfo(name = "cached_unchanged") val cachedUnchanged: Int = 0,
    @ColumnInfo(name = "foreign_ignored") val foreignIgnored: Int = 0,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "retry_attempt") val retryAttempt: Int = 0,
    @ColumnInfo(name = "retry_at") val retryAt: Long? = null,
    @ColumnInfo(name = "last_error_category") val lastErrorCategory: String? = null,
    @ColumnInfo(name = "last_error_subtype") val lastErrorSubtype: String? = null,
    @ColumnInfo(name = "published_run_id") val publishedRunId: String? = null
)

@Entity(
    tableName = "mirror_preview_local_items",
    primaryKeys = ["scan_id", "item_ordinal"],
    foreignKeys = [ForeignKey(
        entity = MirrorPreviewScanEntity::class,
        parentColumns = ["scan_id"],
        childColumns = ["scan_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("scan_id"), Index(value = ["scan_id", "conversation_key"])]
)
data class MirrorPreviewLocalItemEntity(
    @ColumnInfo(name = "scan_id") val scanId: String,
    @ColumnInfo(name = "item_ordinal") val itemOrdinal: Int,
    @ColumnInfo(name = "android_thread_id") val androidThreadId: Long,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "snapshot_hash") val snapshotHash: String,
    @ColumnInfo(name = "local_source_hash") val localSourceHash: String,
    @ColumnInfo(name = "message_count") val messageCount: Int,
    @ColumnInfo(name = "diagnostic_reason") val diagnosticReason: String? = null
)

@Entity(
    tableName = "mirror_preview_remote_items",
    primaryKeys = ["scan_id", "gmail_message_id"],
    foreignKeys = [ForeignKey(
        entity = MirrorPreviewScanEntity::class,
        parentColumns = ["scan_id"],
        childColumns = ["scan_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("scan_id"),
        Index(value = ["scan_id", "conversation_key"]),
        Index(value = ["scan_id", "item_state"]),
        Index(value = ["scan_id", "seen_generation"])
    ]
)
data class MirrorPreviewRemoteItemEntity(
    @ColumnInfo(name = "scan_id") val scanId: String,
    @ColumnInfo(name = "gmail_message_id") val gmailMessageId: String,
    @ColumnInfo(name = "seen_generation") val seenGeneration: Int,
    @ColumnInfo(name = "item_state") val itemState: String,
    @ColumnInfo(name = "conversation_key") val conversationKey: String,
    @ColumnInfo(name = "android_thread_id") val androidThreadId: Long? = null,
    @ColumnInfo(name = "snapshot_hash") val snapshotHash: String? = null,
    @ColumnInfo(name = "identity_version") val identityVersion: String? = null,
    @ColumnInfo(name = "format_version") val formatVersion: String? = null,
    @ColumnInfo(name = "account_binding_valid") val accountBindingValid: Boolean = false,
    @ColumnInfo(name = "device_binding_valid") val deviceBindingValid: Boolean = false,
    @ColumnInfo(name = "label_binding_valid") val labelBindingValid: Boolean = false,
    @ColumnInfo(name = "ownership_valid") val ownershipValid: Boolean = false,
    @ColumnInfo(name = "readable") val readable: Boolean = false,
    @ColumnInfo(name = "cache_matches") val cacheMatches: Boolean = false,
    @ColumnInfo(name = "identity_current") val identityCurrent: Boolean = false,
    @ColumnInfo(name = "reason") val reason: String? = null,
    @ColumnInfo(name = "full_read_attempts") val fullReadAttempts: Int = 0
)
