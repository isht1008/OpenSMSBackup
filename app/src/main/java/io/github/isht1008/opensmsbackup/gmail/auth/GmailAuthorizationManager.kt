package io.github.isht1008.opensmsbackup.gmail.auth

import android.content.Context
import android.accounts.Account
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.api.Scope
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity

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

    fun createAuthorizationRequest(
        profile: AccountProfileEntity
    ): AuthorizationRequest {

        require(profile.accountEmail.isNotBlank()) {
            "Account profile email cannot be blank."
        }

        return AuthorizationRequest.builder()
            .setAccount(
                Account(
                    profile.accountEmail,
                    GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE
                )
            )
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

    fun authorize(
        profile: AccountProfileEntity,
        callback: (
            GmailProfileAuthorizationResult
        ) -> Unit,
        errorCallback: (Exception) -> Unit
    ) {

        authorizationClient
            .authorize(
                createAuthorizationRequest(
                    profile
                )
            )
            .addOnSuccessListener { result ->

                callback(
                    GmailProfileAuthorizationResult(
                        profileId = profile.profileId,
                        accountEmail =
                            profile.accountEmail,
                        authorizationResult = result
                    )
                )

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
