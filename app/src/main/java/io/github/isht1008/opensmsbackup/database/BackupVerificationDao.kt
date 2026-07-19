package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao interface BackupVerificationDao {
    @Insert suspend fun insert(entity: BackupVerificationEntity): Long
    @Query("SELECT * FROM backup_verifications WHERE profile_id = :profileId AND device_id = :deviceId ORDER BY completed_at DESC LIMIT 1")
    fun observeLatest(profileId: String, deviceId: String): Flow<BackupVerificationEntity?>
    @Query("SELECT * FROM backup_verifications WHERE profile_id = :profileId AND device_id = :deviceId ORDER BY completed_at DESC LIMIT 1")
    suspend fun findLatest(profileId: String, deviceId: String): BackupVerificationEntity?
    @Query("SELECT * FROM backup_verifications WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BackupVerificationEntity?
}
