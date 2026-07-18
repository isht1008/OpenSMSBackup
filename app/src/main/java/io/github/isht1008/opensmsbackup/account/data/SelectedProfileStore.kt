package io.github.isht1008.opensmsbackup.account.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class SelectedProfileStore(
    private val context: Context
) {

    private companion object {

        val SELECTED_PROFILE_ID_KEY =
            stringPreferencesKey(
                "selected_profile_id"
            )

        val LEGACY_ACCOUNT_EMAIL_KEY =
            stringPreferencesKey(
                "backup_gmail_account"
            )
    }

    suspend fun getSelectedProfileId(): String? {
        return context.accountPreferencesDataStore
            .data
            .first()[SELECTED_PROFILE_ID_KEY]
    }

    suspend fun getLegacyAccountEmail(): String? {
        return context.accountPreferencesDataStore
            .data
            .first()[LEGACY_ACCOUNT_EMAIL_KEY]
    }

    suspend fun setSelectedProfileId(
        profileId: String
    ) {
        context.accountPreferencesDataStore.edit {
                preferences ->

            preferences[SELECTED_PROFILE_ID_KEY] =
                profileId

            preferences.remove(
                LEGACY_ACCOUNT_EMAIL_KEY
            )
        }
    }

    suspend fun clearSelection() {
        context.accountPreferencesDataStore.edit {
                preferences ->

            preferences.remove(
                SELECTED_PROFILE_ID_KEY
            )

            preferences.remove(
                LEGACY_ACCOUNT_EMAIL_KEY
            )
        }
    }
}
