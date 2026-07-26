package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        account: BackupAccountEntity
    )

    @Query(
        """
        SELECT *
        FROM backup_accounts
        WHERE account_email = :email
        LIMIT 1
        """
    )
    suspend fun findByEmail(
        email: String
    ): BackupAccountEntity?

    @Query(
        """
        SELECT *
        FROM backup_accounts
        WHERE account_id = :accountId
        LIMIT 1
        """
    )
    suspend fun findById(
        accountId: String
    ): BackupAccountEntity?

    @Query(
        """
        SELECT *
        FROM backup_accounts
        ORDER BY is_default DESC, account_email ASC
        """
    )
    suspend fun getAll(): List<BackupAccountEntity>

    @Query("SELECT * FROM backup_accounts")
    fun observeAll(): Flow<List<BackupAccountEntity>>

    @Query(
        """
        UPDATE backup_accounts
        SET is_default = 0
        """
    )
    suspend fun clearDefault()

    @Query(
        """
        UPDATE backup_accounts
        SET is_default = 1
        WHERE account_id = :accountId
        """
    )
    suspend fun setDefault(
        accountId: String
    )

    @Query(
        """
        DELETE FROM backup_accounts
        WHERE account_id = :accountId
        """
    )
    suspend fun delete(
        accountId: String
    )
}
