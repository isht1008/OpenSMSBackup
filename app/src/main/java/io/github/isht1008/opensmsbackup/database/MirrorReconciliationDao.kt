package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MirrorReconciliationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(run: MirrorReconciliationRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<MirrorReconciliationItemEntity>)

    @Transaction
    suspend fun insertPreview(
        run: MirrorReconciliationRunEntity,
        items: List<MirrorReconciliationItemEntity>
    ) {
        invalidateUnconfirmedPreviews(run.profileId, run.deviceId, run.createdAt)
        insertRun(run)
        items.chunked(500).forEach { batch -> insertItems(batch) }
    }

    @Query("UPDATE mirror_reconciliation_runs SET status = 'STALE', terminal_reason = 'SUPERSEDED' WHERE profile_id = :profileId AND device_id = :deviceId AND status = 'PREVIEW' AND created_at < :createdAt")
    suspend fun invalidateUnconfirmedPreviews(profileId: String, deviceId: String, createdAt: Long): Int

    @Query("SELECT * FROM mirror_reconciliation_runs WHERE run_id = :runId LIMIT 1")
    suspend fun findRun(runId: String): MirrorReconciliationRunEntity?

    @Query("SELECT * FROM mirror_reconciliation_items WHERE run_id = :runId AND item_id = :itemId LIMIT 1")
    suspend fun findItem(runId: String, itemId: String): MirrorReconciliationItemEntity?

    @Query("SELECT * FROM mirror_reconciliation_items WHERE run_id = :runId ORDER BY item_id")
    suspend fun findItems(runId: String): List<MirrorReconciliationItemEntity>

    @Query("SELECT * FROM mirror_reconciliation_runs WHERE device_id = :deviceId AND status = 'CONFIRMED' ORDER BY created_at DESC")
    suspend fun findConfirmedRuns(deviceId: String): List<MirrorReconciliationRunEntity>

    @Query("SELECT COUNT(*) FROM mirror_reconciliation_items WHERE run_id = :runId")
    suspend fun itemCount(runId: String): Int

    @Query("SELECT COUNT(*) FROM mirror_reconciliation_items WHERE run_id = :runId AND (state != 'PENDING' OR attempts != 0 OR resulting_gmail_message_id IS NOT NULL OR warning_category IS NOT NULL OR failure_category IS NOT NULL OR completed_at IS NOT NULL)")
    suspend fun mutationOrAmbiguousItemCount(runId: String): Int

    @Query("SELECT * FROM mirror_reconciliation_items WHERE run_id = :runId AND state NOT IN ('COMPLETED','SKIPPED_CONFLICT') ORDER BY item_id")
    suspend fun findIncompleteItems(runId: String): List<MirrorReconciliationItemEntity>

    @Query("SELECT * FROM mirror_reconciliation_runs WHERE profile_id = :profileId AND device_id = :deviceId AND status IN ('CONFIRMED','RUNNING','CANCELLED') ORDER BY created_at DESC LIMIT 1")
    suspend fun findResumableRun(profileId: String, deviceId: String): MirrorReconciliationRunEntity?

    @Query("SELECT COUNT(*) FROM mirror_reconciliation_runs WHERE profile_id = :profileId AND device_id = :deviceId AND status IN ('CONFIRMED','RUNNING')")
    suspend fun activeCount(profileId: String, deviceId: String): Int

    @Query("UPDATE mirror_reconciliation_runs SET status = 'CONFIRMED', confirmed_at = :confirmedAt WHERE run_id = :runId AND profile_id = :profileId AND device_id = :deviceId AND status = 'PREVIEW' AND expires_at >= :confirmedAt")
    suspend fun confirm(runId: String, profileId: String, deviceId: String, confirmedAt: Long): Int

    @Query("UPDATE mirror_reconciliation_runs SET status = :status, completed_at = :completedAt, terminal_reason = :reason WHERE run_id = :runId AND profile_id = :profileId")
    suspend fun updateRunStatus(runId: String, profileId: String, status: String, completedAt: Long?, reason: String?): Int

    @Query("""
        UPDATE mirror_reconciliation_runs
        SET status = 'FAILED', completed_at = :completedAt, terminal_reason = 'FOREGROUND_START_FAILED'
        WHERE run_id = :runId AND profile_id = :profileId AND status = 'CONFIRMED'
          AND NOT EXISTS (
              SELECT 1 FROM mirror_reconciliation_items
              WHERE run_id = :runId
                AND (state != 'PENDING' OR attempts != 0 OR resulting_gmail_message_id IS NOT NULL
                     OR warning_category IS NOT NULL OR failure_category IS NOT NULL OR completed_at IS NOT NULL)
          )
    """)
    suspend fun failConfirmedForegroundStartIfAllPending(runId: String, profileId: String, completedAt: Long): Int

    @Query("UPDATE mirror_reconciliation_items SET state = :state, attempts = attempts + :incrementAttempts, resulting_gmail_message_id = :resultingId, warning_category = :warning, failure_category = :failure, completed_at = :completedAt WHERE run_id = :runId AND item_id = :itemId AND profile_id = :profileId")
    suspend fun updateItem(
        runId: String,
        itemId: String,
        profileId: String,
        state: String,
        incrementAttempts: Int = 0,
        resultingId: String? = null,
        warning: String? = null,
        failure: String? = null,
        completedAt: Long? = null
    ): Int

    @Query("UPDATE mirror_reconciliation_runs SET status = 'STALE', terminal_reason = 'POLICY_CHANGED' WHERE profile_id = :profileId AND status = 'PREVIEW'")
    suspend fun invalidatePendingForPolicyChange(profileId: String): Int
}
