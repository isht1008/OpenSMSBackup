package io.github.isht1008.opensmsbackup.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountProfileDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(
        profile: AccountProfileEntity
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettings(
        settings: AccountSettingsEntity
    )

    @Update
    suspend fun updateProfile(
        profile: AccountProfileEntity
    )

    @Update
    suspend fun updateSettings(
        settings: AccountSettingsEntity
    )

    @Query(
        """
        SELECT *
        FROM account_profiles
        ORDER BY account_email COLLATE NOCASE ASC
        """
    )
    fun observeProfiles(): Flow<List<AccountProfileEntity>>

    @Query(
        """
        SELECT *
        FROM account_profiles
        WHERE profile_id = :profileId
        LIMIT 1
        """
    )
    suspend fun findProfileById(
        profileId: String
    ): AccountProfileEntity?

    @Query(
        """
        SELECT *
        FROM account_profiles
        WHERE account_email = :email COLLATE NOCASE
        LIMIT 1
        """
    )
    suspend fun findProfileByEmail(
        email: String
    ): AccountProfileEntity?

    @Query(
        """
        SELECT *
        FROM account_profiles
        WHERE provider_account_id = :providerAccountId
        LIMIT 1
        """
    )
    suspend fun findProfileByProviderId(
        providerAccountId: String
    ): AccountProfileEntity?

    @Query(
        """
        SELECT *
        FROM account_settings
        WHERE profile_id = :profileId
        LIMIT 1
        """
    )
    suspend fun findSettings(
        profileId: String
    ): AccountSettingsEntity?

    @Query(
        """
        UPDATE account_profiles
        SET connection_state = :connectionState,
            updated_time = :updatedTime
        WHERE profile_id = :profileId
        """
    )
    suspend fun updateConnectionState(
        profileId: String,
        connectionState: String,
        updatedTime: Long
    )

    @Transaction
    suspend fun insertProfileWithSettings(
        profile: AccountProfileEntity,
        settings: AccountSettingsEntity
    ) {
        insertProfile(profile)
        insertSettings(settings)
    }
}
