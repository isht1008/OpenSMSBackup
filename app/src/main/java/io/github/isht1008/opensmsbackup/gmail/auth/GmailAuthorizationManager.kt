package io.github.isht1008.opensmsbackup.gmail.auth

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class GmailAuthorizationManager(
    private val context: Context
) {

    companion object {

        const val GMAIL_SCOPE =
            "https://www.googleapis.com/auth/gmail.modify"
    }

    private val authorizationClient =
        Identity.getAuthorizationClient(context)

    fun createAuthorizationRequest(): AuthorizationRequest {

        return AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(GMAIL_SCOPE)
                )
            )
            .build()

    }

    fun authorize(
        callback: (AuthorizationResult) -> Unit,
        errorCallback: (Exception) -> Unit
    ) {

        authorizationClient
            .authorize(
                createAuthorizationRequest()
            )
            .addOnSuccessListener { result ->

                callback(result)

            }
            .addOnFailureListener { exception ->

                errorCallback(exception)

            }

    }

    fun revokeAccess(
        callback: () -> Unit,
        errorCallback: (Exception) -> Unit
    ) {


    }

}