package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConversationSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        entity: ConversationSnapshotEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM conversation_snapshots
        WHERE account_id = :accountId
        AND android_thread_id = :androidThreadId
        LIMIT 1
        """
    )
    suspend fun find(
        accountId: String,
        androidThreadId: Long
    ): ConversationSnapshotEntity?

    @Query(
        """
        SELECT COUNT(*)
        FROM conversation_snapshots
        WHERE account_id = :accountId
        """
    )
    suspend fun count(
        accountId: String
    ): Int

    @Query(
        """
        DELETE FROM conversation_snapshots
        WHERE account_id = :accountId
        """
    )
    suspend fun deleteAccount(
        accountId: String
    )
}
