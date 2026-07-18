package io.github.isht1008.opensmsbackup.account.data

import android.content.Context
import io.github.isht1008.opensmsbackup.database.AccountProfileDao
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.AccountSettingsEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID

class MultiAccountRepository(
    private val accountDao: AccountProfileDao,
    private val selectedProfileStore: SelectedProfileStore
) {

    fun observeProfiles(): Flow<List<AccountProfileEntity>> {
        return accountDao.observeProfiles()
    }

    suspend fun getProfile(
        profileId: String
    ): AccountProfileEntity? {
        return accountDao.findProfileById(
            profileId
        )
    }

    suspend fun getSettings(
        profileId: String
    ): AccountSettingsEntity? {
        return accountDao.findSettings(
            profileId
        )
    }

    suspend fun getSelectedProfile(): AccountProfileEntity? {
        val selectedProfileId =
            selectedProfileStore
                .getSelectedProfileId()

        if (selectedProfileId != null) {
            return accountDao.findProfileById(
                selectedProfileId
            )
        }

        return migrateLegacySelection()
    }

    suspend fun createOrReconnectProfile(
        accountEmail: String,
        providerAccountId: String? = null,
        displayName: String? = null,
        photoUrl: String? = null
    ): AccountProfileEntity {
        val normalizedEmail =
            normalizeEmail(accountEmail)

        val existingProfile =
            providerAccountId
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    accountDao.findProfileByProviderId(
                        it
                    )
                }
                ?: accountDao.findProfileByEmail(
                    normalizedEmail
                )

        val now =
            System.currentTimeMillis()

        if (existingProfile != null) {
            val updatedProfile =
                existingProfile.copy(
                    providerAccountId =
                        providerAccountId
                            ?: existingProfile
                                .providerAccountId,
                    accountEmail = normalizedEmail,
                    displayName =
                        displayName
                            ?: existingProfile.displayName,
                    photoUrl =
                        photoUrl
                            ?: existingProfile.photoUrl,
                    connectionState =
                        AccountProfileEntity
                            .CONNECTION_STATE_CONNECTED,
                    updatedTime = now
                )

            accountDao.updateProfile(
                updatedProfile
            )

            ensureSettings(
                updatedProfile.profileId
            )

            return updatedProfile
        }

        val profile =
            AccountProfileEntity(
                profileId =
                    UUID.randomUUID().toString(),
                providerAccountId =
                    providerAccountId
                        ?.takeIf { it.isNotBlank() },
                accountEmail = normalizedEmail,
                displayName = displayName,
                photoUrl = photoUrl,
                createdTime = now,
                updatedTime = now
            )

        accountDao.insertProfileWithSettings(
            profile = profile,
            settings =
                AccountSettingsEntity(
                    profileId = profile.profileId
                )
        )

        return profile
    }

    suspend fun selectProfile(
        profileId: String
    ) {
        requireNotNull(
            accountDao.findProfileById(profileId)
        ) {
            "Cannot select an unknown account profile."
        }

        selectedProfileStore
            .setSelectedProfileId(
                profileId
            )
    }

    suspend fun createOrReconnectAndSelect(
        accountEmail: String,
        providerAccountId: String? = null,
        displayName: String? = null,
        photoUrl: String? = null
    ): AccountProfileEntity {
        val profile =
            createOrReconnectProfile(
                accountEmail = accountEmail,
                providerAccountId =
                    providerAccountId,
                displayName = displayName,
                photoUrl = photoUrl
            )

        selectProfile(
            profile.profileId
        )

        return profile
    }

    suspend fun disconnectSelectedProfile() {
        val selectedProfileId =
            selectedProfileStore
                .getSelectedProfileId()

        if (selectedProfileId != null) {
            accountDao.updateConnectionState(
                profileId = selectedProfileId,
                connectionState =
                    AccountProfileEntity
                        .CONNECTION_STATE_DISCONNECTED,
                updatedTime =
                    System.currentTimeMillis()
            )
        }

        selectedProfileStore.clearSelection()
    }

    suspend fun disconnectProfile(
        profileId: String
    ) {
        requireNotNull(
            accountDao.findProfileById(
                profileId
            )
        ) {
            "Cannot disconnect an unknown account profile."
        }

        accountDao.updateConnectionState(
            profileId = profileId,
            connectionState =
                AccountProfileEntity
                    .CONNECTION_STATE_DISCONNECTED,
            updatedTime = System.currentTimeMillis()
        )

        if (
            selectedProfileStore
                .getSelectedProfileId() ==
            profileId
        ) {
            selectedProfileStore.clearSelection()
        }
    }

    suspend fun updateConnectionState(
        profileId: String,
        connectionState: String
    ) {
        requireNotNull(
            accountDao.findProfileById(
                profileId
            )
        ) {
            "Cannot update an unknown account profile."
        }

        accountDao.updateConnectionState(
            profileId = profileId,
            connectionState = connectionState,
            updatedTime = System.currentTimeMillis()
        )
    }

    suspend fun updateSettings(
        settings: AccountSettingsEntity
    ) {
        requireNotNull(
            accountDao.findProfileById(
                settings.profileId
            )
        ) {
            "Cannot update settings for an unknown account profile."
        }

        accountDao.updateSettings(settings)
    }

    private suspend fun migrateLegacySelection(): AccountProfileEntity? {
        val legacyEmail =
            selectedProfileStore
                .getLegacyAccountEmail()
                ?.takeIf { it.isNotBlank() }
                ?: return null

        val profile =
            accountDao.findProfileByEmail(
                normalizeEmail(legacyEmail)
            )
                ?: createOrReconnectProfile(
                    accountEmail = legacyEmail
                )

        selectedProfileStore
            .setSelectedProfileId(
                profile.profileId
            )

        return profile
    }

    private suspend fun ensureSettings(
        profileId: String
    ) {
        if (
            accountDao.findSettings(profileId) ==
            null
        ) {
            accountDao.insertSettings(
                AccountSettingsEntity(
                    profileId = profileId
                )
            )
        }
    }

    private fun normalizeEmail(
        accountEmail: String
    ): String {
        return accountEmail
            .trim()
            .lowercase(Locale.ROOT)
            .also { normalizedEmail ->
                require(normalizedEmail.isNotBlank()) {
                    "Account email cannot be blank."
                }
            }
    }

    companion object {

        fun create(
            context: Context
        ): MultiAccountRepository {
            val database =
                DatabaseProvider.getDatabase(
                    context
                )

            return MultiAccountRepository(
                accountDao =
                    database.accountProfileDao(),
                selectedProfileStore =
                    SelectedProfileStore(
                        context.applicationContext
                    )
            )
        }
    }
}
