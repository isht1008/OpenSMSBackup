package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

data class MirrorPreviewRemoteProgress(
    val total: Int,
    val ignored: Int,
    val required: Int,
    val validatedFull: Int,
    val cachedUnchanged: Int,
    val incomplete: Int
)

@Dao
interface MirrorPreviewScanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScan(scan: MirrorPreviewScanEntity)

    @Update
    suspend fun updateScan(scan: MirrorPreviewScanEntity): Int

    @Query("SELECT * FROM mirror_preview_scans WHERE scan_id = :scanId LIMIT 1")
    suspend fun findScan(scanId: String): MirrorPreviewScanEntity?

    @Query("SELECT * FROM mirror_preview_scans WHERE profile_id = :profileId AND device_id = :deviceId AND lifecycle_state IN ('ENQUEUED','RUNNING','WAITING_NETWORK','PAUSED','FINALIZING') AND expires_at >= :now ORDER BY created_at DESC LIMIT 1")
    suspend fun findActive(profileId: String, deviceId: String, now: Long): MirrorPreviewScanEntity?

    @Query("SELECT * FROM mirror_preview_scans WHERE lifecycle_state IN ('ENQUEUED','RUNNING','WAITING_NETWORK','PAUSED','FINALIZING') ORDER BY updated_at ASC LIMIT :limit")
    suspend fun findRecoveryCandidates(limit: Int): List<MirrorPreviewScanEntity>

    @Query("""
        SELECT scan.* FROM mirror_preview_scans AS scan
        INNER JOIN mirror_reconciliation_runs AS run
            ON run.run_id = scan.published_run_id
        WHERE scan.lifecycle_state = 'PUBLISHED'
          AND run.status = 'PREVIEW'
          AND run.expires_at >= :now
        ORDER BY scan.updated_at DESC
        LIMIT 1
    """)
    suspend fun findPublishedAwaitingConfirmation(now: Long): MirrorPreviewScanEntity?

    @Query("UPDATE mirror_preview_scans SET lifecycle_state = 'SUPERSEDED', updated_at = :now WHERE profile_id = :profileId AND device_id = :deviceId AND scan_id != :keepScanId AND lifecycle_state IN ('ENQUEUED','RUNNING','WAITING_NETWORK','PAUSED','FAILED')")
    suspend fun supersedeOthers(profileId: String, deviceId: String, keepScanId: String, now: Long): Int

    @Query("UPDATE mirror_preview_scans SET lifecycle_state = 'EXPIRED', stage = 'PAUSED', updated_at = :now WHERE expires_at < :now AND lifecycle_state IN ('ENQUEUED','RUNNING','WAITING_NETWORK','PAUSED','FINALIZING','FAILED')")
    suspend fun expireAbandoned(now: Long): Int

    @Query("DELETE FROM mirror_preview_scans WHERE lifecycle_state IN ('CANCELLED','EXPIRED','SUPERSEDED','PUBLISHED') AND updated_at < :before")
    suspend fun deleteTerminalBefore(before: Long): Int

    @Query("DELETE FROM mirror_preview_scans WHERE scan_id = :scanId AND lifecycle_state IN ('CANCELLED','EXPIRED','SUPERSEDED','PUBLISHED','FAILED')")
    suspend fun deleteReusableScanSlot(scanId: String): Int

    @Query("""
        UPDATE mirror_preview_scans
        SET lifecycle_state = 'PAUSED', stage = 'PAUSED', updated_at = :now,
            last_error_category = :category
        WHERE scan_id = :scanId
          AND lifecycle_state IN ('ENQUEUED','RUNNING','WAITING_NETWORK','FINALIZING')
    """)
    suspend fun markInterruptedIfResumable(scanId: String, now: Long, category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalItems(items: List<MirrorPreviewLocalItemEntity>)

    @Query("DELETE FROM mirror_preview_local_items WHERE scan_id = :scanId")
    suspend fun deleteLocalItems(scanId: String): Int

    @Query("SELECT * FROM mirror_preview_local_items WHERE scan_id = :scanId ORDER BY item_ordinal")
    suspend fun findLocalItems(scanId: String): List<MirrorPreviewLocalItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteItems(items: List<MirrorPreviewRemoteItemEntity>)

    @Query("SELECT * FROM mirror_preview_remote_items WHERE scan_id = :scanId ORDER BY gmail_message_id")
    suspend fun findRemoteItems(scanId: String): List<MirrorPreviewRemoteItemEntity>

    @Query("SELECT * FROM mirror_preview_remote_items WHERE scan_id = :scanId AND item_state = :state ORDER BY gmail_message_id")
    suspend fun findRemoteItemsByState(scanId: String, state: String): List<MirrorPreviewRemoteItemEntity>

    @Query("SELECT * FROM mirror_preview_remote_items WHERE scan_id = :scanId AND gmail_message_id IN (:messageIds)")
    suspend fun findRemoteItemsByIds(
        scanId: String,
        messageIds: List<String>
    ): List<MirrorPreviewRemoteItemEntity>

    @Query("SELECT gmail_message_id FROM mirror_preview_remote_items WHERE scan_id = :scanId AND seen_generation = :generation")
    suspend fun findSeenMessageIds(scanId: String, generation: Int): List<String>

    @Query("""
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN item_state = 'IGNORED' THEN 1 ELSE 0 END), 0) AS ignored,
            COALESCE(SUM(CASE WHEN item_state = 'REQUIRED' THEN 1 ELSE 0 END), 0) AS required,
            COALESCE(SUM(CASE WHEN item_state = 'VALIDATED' AND cache_matches = 0 THEN 1 ELSE 0 END), 0) AS validatedFull,
            COALESCE(SUM(CASE WHEN item_state = 'VALIDATED' AND cache_matches = 1 THEN 1 ELSE 0 END), 0) AS cachedUnchanged,
            COALESCE(SUM(CASE WHEN item_state NOT IN ('VALIDATED', 'IGNORED') THEN 1 ELSE 0 END), 0) AS incomplete
        FROM mirror_preview_remote_items
        WHERE scan_id = :scanId AND seen_generation = :generation
    """)
    suspend fun remoteProgress(scanId: String, generation: Int): MirrorPreviewRemoteProgress

    @Query("DELETE FROM mirror_preview_remote_items WHERE scan_id = :scanId AND seen_generation != :generation")
    suspend fun deleteOtherGenerations(scanId: String, generation: Int): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliationRun(run: MirrorReconciliationRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliationItems(items: List<MirrorReconciliationItemEntity>)

    @Query("UPDATE mirror_reconciliation_runs SET status = 'STALE', terminal_reason = 'SUPERSEDED' WHERE profile_id = :profileId AND device_id = :deviceId AND status = 'PREVIEW' AND created_at < :createdAt")
    suspend fun invalidateOlderPreviews(profileId: String, deviceId: String, createdAt: Long): Int

    @Query("UPDATE mirror_reconciliation_runs SET status = 'STALE', terminal_reason = 'PREVIEW_CANCELLED' WHERE run_id = :runId AND status = 'PREVIEW'")
    suspend fun invalidateCancelledPublishedPreview(runId: String): Int

    @Transaction
    suspend fun replaceLocalCheckpoint(scan: MirrorPreviewScanEntity, items: List<MirrorPreviewLocalItemEntity>) {
        deleteLocalItems(scan.scanId)
        items.chunked(500).forEach { upsertLocalItems(it) }
        check(updateScan(scan) == 1)
    }

    @Transaction
    suspend fun publishExecutablePreview(
        scanId: String,
        run: MirrorReconciliationRunEntity,
        items: List<MirrorReconciliationItemEntity>,
        now: Long
    ) {
        val scan = requireNotNull(findScan(scanId))
        require(scan.lifecycleState == "FINALIZING")
        require(scan.expiresAt >= now && scan.publishedRunId == null)
        require(scan.localScanComplete && scan.localCountConsistent)
        require(scan.localDatasetFingerprint != null && scan.remoteDiscoveryComplete)
        require(scan.remoteDiscoveryFingerprint != null)
        require(remoteProgress(scanId, scan.remoteGeneration).incomplete == 0)
        require(run.status == "PREVIEW" && run.runId != scanId)
        require(run.profileId == scan.profileId && run.deviceId == scan.deviceId)
        require(run.localDatasetFingerprint == scan.localDatasetFingerprint)
        invalidateOlderPreviews(run.profileId, run.deviceId, run.createdAt)
        insertReconciliationRun(run)
        items.chunked(500).forEach { insertReconciliationItems(it) }
        check(updateScan(scan.copy(
            lifecycleState = "PUBLISHED",
            stage = "READY",
            updatedAt = now,
            publishedRunId = run.runId
        )) == 1)
    }

    @Transaction
    suspend fun cancelScan(scanId: String, now: Long, category: String) {
        val scan = findScan(scanId) ?: return
        scan.publishedRunId?.let { invalidateCancelledPublishedPreview(it) }
        check(updateScan(scan.copy(
            lifecycleState = "CANCELLED",
            stage = "PAUSED",
            updatedAt = now,
            lastErrorCategory = category
        )) == 1)
    }
}
