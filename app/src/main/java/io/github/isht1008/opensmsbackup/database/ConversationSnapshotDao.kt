package io.github.isht1008.opensmsbackup.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

data class LocalSourceCheckpointUpdate(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "local_source_hash") val localSourceHash: String,
    @ColumnInfo(name = "local_source_message_count") val localSourceMessageCount: Int,
    @ColumnInfo(name = "local_source_last_message_date") val localSourceLastMessageDate: Long,
    @ColumnInfo(name = "local_source_max_sms_id") val localSourceMaxSmsId: Long,
    @ColumnInfo(name = "local_source_device_id") val localSourceDeviceId: String
)

@Dao
interface ConversationSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversationSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ConversationSnapshotEntity>): List<Long>

    @Update(entity = ConversationSnapshotEntity::class)
    suspend fun updateLocalSourceCheckpoints(updates: List<LocalSourceCheckpointUpdate>): Int

    @Query("SELECT * FROM conversation_snapshots WHERE account_id = :accountId AND android_thread_id = :androidThreadId LIMIT 1")
    suspend fun find(accountId: String, androidThreadId: Long): ConversationSnapshotEntity?

    @Query("SELECT * FROM conversation_snapshots WHERE account_id = :accountId")
    suspend fun findAllForAccount(accountId: String): List<ConversationSnapshotEntity>

    @Query("SELECT * FROM conversation_snapshots WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun findAllForProfile(profileId: String, accountId: String): List<ConversationSnapshotEntity>

    @Query("SELECT * FROM conversation_snapshots WHERE profile_id = :profileId AND account_id = :accountId AND android_thread_id = :androidThreadId LIMIT 1")
    suspend fun findForProfile(profileId: String, accountId: String, androidThreadId: Long): ConversationSnapshotEntity?

    @Query("UPDATE conversation_snapshots SET local_source_hash = :localSourceHash, local_source_message_count = :messageCount, local_source_last_message_date = :lastMessageDate, local_source_max_sms_id = :maxSmsId WHERE profile_id = :profileId AND account_id = :accountId AND android_thread_id = :androidThreadId")
    suspend fun updateLocalSourceCheckpoint(profileId: String, accountId: String, androidThreadId: Long, localSourceHash: String, messageCount: Int, lastMessageDate: Long, maxSmsId: Long): Int

    @Query("DELETE FROM conversation_snapshots WHERE profile_id = :profileId AND account_id = :accountId AND android_thread_id = :androidThreadId AND gmail_message_id = :expectedGmailMessageId")
    suspend fun deleteOwnedSnapshot(profileId: String, accountId: String, androidThreadId: Long, expectedGmailMessageId: String): Int

    @Query("SELECT COUNT(*) FROM conversation_snapshots WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun countForProfile(profileId: String, accountId: String): Int

    @Query("DELETE FROM conversation_snapshots WHERE profile_id = :profileId AND account_id = :accountId")
    suspend fun deleteProfileAccount(profileId: String, accountId: String)

    @Query("SELECT COUNT(*) FROM conversation_snapshots WHERE account_id = :accountId")
    suspend fun count(accountId: String): Int

    @Query("DELETE FROM conversation_snapshots WHERE account_id = :accountId")
    suspend fun deleteAccount(accountId: String)
}
