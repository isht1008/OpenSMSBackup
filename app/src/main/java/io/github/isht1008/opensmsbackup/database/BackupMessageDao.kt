package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackupMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        entity: BackupMessageEntity
    ): Long

    @Query(
        """
    SELECT *
    FROM backup_messages
    WHERE account_email = :accountEmail
    AND fingerprint = :fingerprint
    LIMIT 1
    """
    )
    suspend fun findByFingerprint(
        accountEmail: String,
        fingerprint: String
    ): BackupMessageEntity?

    @Query(
        """
    SELECT *
    FROM backup_messages
    WHERE account_id = :accountId
    AND thread_id = :androidThreadId
    AND gmail_thread_id IS NOT NULL
    ORDER BY message_date DESC, sms_id DESC
    LIMIT 1
    """
    )
    suspend fun findLatestThreadMessage(
        accountId: String,
        androidThreadId: Long
    ): BackupMessageEntity?

    @Query(
        """
    SELECT COUNT(*)
    FROM backup_messages
    WHERE account_email = :accountEmail
    """
    )
    suspend fun count(
        accountEmail: String
    ): Int

    @Query(
        """
    DELETE FROM backup_messages
    WHERE account_email = :accountEmail
    """
    )
    suspend fun deleteAccount(
        accountEmail: String
    )

    @Query(
        """
    UPDATE backup_messages
    SET gmail_message_id = :gmailId
    WHERE id = :id
    """
    )
    suspend fun updateGmailId(
        id: Long,
        gmailId: String
    )

}