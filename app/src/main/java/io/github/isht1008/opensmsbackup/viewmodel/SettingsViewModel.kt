package io.github.isht1008.opensmsbackup.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthState
import kotlinx.coroutines.launch


class SettingsViewModel(
    private val gmailAccountManager: GmailAccountManager,
    private val gmailAccountCoordinator: GmailAccountCoordinator
) : ViewModel() {


    var authState by mutableStateOf<GmailAuthState>(
        GmailAuthState.NotConnected
    )
        private set


    val isConnected: Boolean
        get() = authState is GmailAuthState.Connected


    val gmailAccount: String?
        get() =
            (authState as? GmailAuthState.Connected)
                ?.email


    val isConnecting: Boolean
        get() =
            authState is GmailAuthState.Connecting


    init {
        loadSavedAccount()
    }


    private fun loadSavedAccount() {

        viewModelScope.launch {

            val account =
                gmailAccountManager.getAccount()

            if (account != null) {

                authState =
                    GmailAuthState.Connected(
                        account
                    )
            }
        }
    }


    fun connectAccount() {

        viewModelScope.launch {

            authState =
                GmailAuthState.Connecting


            val result =
                gmailAccountCoordinator
                    .connectAccount()


            result.onSuccess { email ->

                authState =
                    GmailAuthState.Connected(
                        email
                    )

            }.onFailure { error ->

                authState =
                    GmailAuthState.Error(
                        error.message
                            ?: "Unknown error"
                    )
            }
        }
    }


    fun removeAccount() {

        viewModelScope.launch {

            gmailAccountCoordinator
                .removeAccount()

            authState =
                GmailAuthState.NotConnected
        }
    }


    fun setError(
        message: String
    ) {

        authState =
            GmailAuthState.Error(
                message
            )
    }
}