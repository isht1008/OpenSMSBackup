package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager
import io.github.isht1008.opensmsbackup.gmail.auth.GoogleSignInManager
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.suspendCancellableCoroutine
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

        var preparedProfile:
                AccountProfileEntity? = null

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

            preparedProfile = profile

            val authorizationResult =
                authorizeGmail(
                    profile
                )


            if (authorizationResult) {

                accountManager
                    .selectAccountProfile(
                        profile
                    )

                Result.success(
                    profile.accountEmail
                )

            } else {

                accountManager
                    .markAuthorizationRequired(
                        profile
                    )

                Result.failure(
                    Exception(
                        "Gmail permission not granted"
                    )
                )
            }


        } catch (e: Exception) {

            preparedProfile?.let { profile ->
                runCatching {
                    accountManager
                        .markAuthorizationRequired(
                            profile
                        )
                }
            }

            Result.failure(e)

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


    suspend fun removeAccount() {

        accountManager.clearAccount()

    }


    suspend fun getSavedAccount(): String? {

        return accountManager.getAccount()

    }
}
