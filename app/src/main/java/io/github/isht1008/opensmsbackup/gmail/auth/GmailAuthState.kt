package io.github.isht1008.opensmsbackup.gmail.auth

sealed interface GmailAuthState {

    data object NotConnected : GmailAuthState

    data object Connecting : GmailAuthState

    data class Connected(
        val email: String
    ) : GmailAuthState

    data class Error(
        val message: String
    ) : GmailAuthState
}