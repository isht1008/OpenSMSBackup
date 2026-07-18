package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager
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


        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {

            Result.failure(e)

        }
    }

    suspend fun authorizeAccount(
        profile: AccountProfileEntity
    ): Result<AccountProfileEntity> {

        return try {
            val authorized =
                authorizeGmail(profile)

            if (!authorized) {
                throw IllegalStateException(
                    "Gmail permission not granted"
                )
            }

            accountManager.markConnected(
                profile
            )

            Result.success(
                profile.copy(
                    connectionState =
                        AccountProfileEntity
                            .CONNECTION_STATE_CONNECTED
                )
            )

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            runCatching {
                accountManager
                    .markAuthorizationRequired(
                        profile
                    )
            }

            Result.failure(error)
        }
    }

    suspend fun disconnectAccount(
        profile: AccountProfileEntity
    ) {

        accountManager.disconnectAccountProfile(
            profile
        )
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
