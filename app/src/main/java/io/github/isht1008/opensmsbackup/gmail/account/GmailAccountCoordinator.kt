package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import android.content.Intent
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationResolutionRequiredException
import io.github.isht1008.opensmsbackup.gmail.auth.GoogleSignInManager
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class GmailAccountCoordinator(
    private val context: Context
) {


    private val googleSignInManager =
        GoogleSignInManager(
            context
        )


    private val gmailAuthorizationManager =
        GmailAuthorizationManager(
            context
        )


    private val accountManager =
        GmailAccountManager(
            context
        )

    private val authorizationFlow = GmailAccountAuthorizationFlow(
        object : GmailAccountAuthorizationOperations {
            override suspend fun requestGmailAuthorization(profile: AccountProfileEntity) {
                if (!authorizeGmail(profile)) error("Gmail permission not granted")
            }

            override suspend fun markConnected(profile: AccountProfileEntity) =
                accountManager.markConnected(profile)

            override suspend fun markAuthorizationRequired(profile: AccountProfileEntity) =
                accountManager.markAuthorizationRequired(profile)
        }
    )


    suspend fun connectAccount(): Result<String> {

        return try {

            val email =
                googleSignInManager
                    .signIn()
                    .getOrThrow()

            val profile =
                accountManager
                    .prepareAccountProfile(
                        email
                    )

            val authorizedProfile =
                authorizeAccount(profile)
                    .getOrThrow()

            accountManager
                .selectAccountProfile(
                    authorizedProfile
                )

            Result.success(
                authorizedProfile.accountEmail
            )


        } catch (resolution: GmailAuthorizationResolutionRequiredException) {
            Result.failure(
                GmailAuthorizationResolutionRequiredException(
                    profileId = resolution.profileId,
                    resolution = resolution.resolution,
                    selectAfterAuthorization = true
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {

            Result.failure(e)

        }
    }

    suspend fun authorizeAccount(
        profile: AccountProfileEntity
    ): Result<AccountProfileEntity> = authorizationFlow.authorize(profile)

    suspend fun disconnectAccount(
        profile: AccountProfileEntity
    ) {
        googleSignInManager.clearCredentialState()
        accountManager.disconnectAccountProfile(
            profile
        )
    }

    suspend fun completeAuthorization(
        profileId: String,
        resultIntent: Intent,
        selectAfterAuthorization: Boolean
    ): Result<AccountProfileEntity> {
        val profile = accountManager.getAccountProfile(profileId)
            ?: return Result.failure(IllegalStateException("Account profile is unavailable."))
        return try {
            val result = gmailAuthorizationManager.authorizationResultFromIntent(resultIntent)
            if (!gmailAuthorizationManager.isAuthorized(result)) {
                throw IllegalStateException("Gmail authorization was not granted.")
            }
            accountManager.markConnected(profile)
            val connected = profile.copy(
                connectionState = AccountProfileEntity.CONNECTION_STATE_CONNECTED
            )
            if (selectAfterAuthorization) accountManager.selectAccountProfile(connected)
            Result.success(connected)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            runCatching { accountManager.markAuthorizationRequired(profile) }
            Result.failure(error)
        }
    }


    private suspend fun authorizeGmail(
        profile: AccountProfileEntity
    ): Boolean {

        return suspendCancellableCoroutine { continuation ->


            gmailAuthorizationManager.authorize(

                profile = profile,

                callback = {

                    continuation.resume(
                        true
                    )

                },

                errorCallback = {

                    continuation.resumeWithException(
                        it
                    )

                }

            )

        }
    }


}
