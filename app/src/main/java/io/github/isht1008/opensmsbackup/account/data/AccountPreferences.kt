package io.github.isht1008.opensmsbackup.account.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.accountPreferencesDataStore by
preferencesDataStore(
    name = "gmail_account"
)
