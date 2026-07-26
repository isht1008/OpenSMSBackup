package io.github.isht1008.opensmsbackup.gmail.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GmailAuthorizationManager(context: Context) {
    companion object {
        const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.modify"
        val requestedScopes: List<Scope> get() = listOf(Scope(GMAIL_SCOPE))
    }

    private val authorizationClient = Identity.getAuthorizationClient(context)

    fun createAuthorizationRequest(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(requestedScopes)
        .build()

    fun createAuthorizationRequest(profile: AccountProfileEntity): AuthorizationRequest {
        require(profile.accountEmail.isNotBlank()) { "Account profile email cannot be blank." }
        return AuthorizationRequest.builder()
            .setAccount(Account(profile.accountEmail, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(requestedScopes)
            .build()
    }

    fun authorize(
        callback: (AuthorizationResult) -> Unit,
        errorCallback: (Exception) -> Unit
    ) {
        authorizationClient.authorize(createAuthorizationRequest())
            .addOnSuccessListener(callback)
            .addOnFailureListener(errorCallback)
    }

    fun authorize(
        profile: AccountProfileEntity,
        callback: (GmailProfileAuthorizationResult) -> Unit,
        errorCallback: (Exception) -> Unit
    ) {
        authorizationClient.authorize(createAuthorizationRequest(profile))
            .addOnSuccessListener { result ->
                when {
                    authorizationDecision(result.hasResolution(), result.grantedScopes) ==
                        GmailAuthorizationDecision.USER_RESOLUTION_REQUIRED -> errorCallback(
                        GmailAuthorizationResolutionRequiredException(
                            profileId = profile.profileId,
                            resolution = requireNotNull(result.pendingIntent)
                        )
                    )
                    isAuthorized(result) -> callback(
                        GmailProfileAuthorizationResult(profile.profileId, profile.accountEmail, result)
                    )
                    else -> errorCallback(IllegalStateException("Gmail authorization was not granted."))
                }
            }
            .addOnFailureListener(errorCallback)
    }

    suspend fun revokeAccess(profile: AccountProfileEntity) {
        require(profile.accountEmail.isNotBlank()) { "Account profile email cannot be blank." }
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(profile.accountEmail, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
            .setScopes(requestedScopes)
            .build()
        suspendCancellableCoroutine { continuation ->
            authorizationClient.revokeAccess(request)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }

    fun authorizationResultFromIntent(intent: Intent): AuthorizationResult =
        authorizationClient.getAuthorizationResultFromIntent(intent)

    fun isAuthorized(result: AuthorizationResult): Boolean =
        authorizationDecision(result.hasResolution(), result.grantedScopes) ==
            GmailAuthorizationDecision.AUTHORIZED
}

enum class GmailAuthorizationDecision { AUTHORIZED, USER_RESOLUTION_REQUIRED, DENIED }

fun authorizationDecision(
    hasResolution: Boolean,
    grantedScopes: List<String>
): GmailAuthorizationDecision = when {
    hasResolution -> GmailAuthorizationDecision.USER_RESOLUTION_REQUIRED
    grantedScopes.any { it == GmailAuthorizationManager.GMAIL_SCOPE } -> GmailAuthorizationDecision.AUTHORIZED
    else -> GmailAuthorizationDecision.DENIED
}
