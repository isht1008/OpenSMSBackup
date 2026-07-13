package io.github.isht1008.opensmsbackup.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    var gmailAccount by mutableStateOf<String?>(null)
        private set

    var isConnecting by mutableStateOf(false)
        private set

    val isConnected: Boolean
        get() = gmailAccount != null

    fun updateConnecting(connecting: Boolean) {
        isConnecting = connecting
    }

    fun updateGmailAccount(email: String?) {
        gmailAccount = email
    }

    fun clearGmailAccount() {
        gmailAccount = null
    }
}