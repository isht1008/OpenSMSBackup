package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first


private val Context.dataStore by preferencesDataStore(
    name = "gmail_account"
)


class GmailAccountManager(
    private val context: Context
) {

    companion object {

        private val GMAIL_ACCOUNT_KEY =
            stringPreferencesKey(
                "backup_gmail_account"
            )
    }


    suspend fun saveAccount(
        email: String
    ) {

        context.dataStore.edit { preferences ->

            preferences[GMAIL_ACCOUNT_KEY] = email

        }
    }


    suspend fun getAccount(): String? {

        val preferences =
            context.dataStore.data.first()

        return preferences[
            GMAIL_ACCOUNT_KEY
        ]
    }


    suspend fun hasAccount(): Boolean {

        return getAccount() != null
    }


    suspend fun clearAccount() {

        context.dataStore.edit { preferences ->

            preferences.remove(
                GMAIL_ACCOUNT_KEY
            )
        }
    }
}