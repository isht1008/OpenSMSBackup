package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager
import io.github.isht1008.opensmsbackup.gmail.auth.GoogleSignInManager
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

        return try {

            val email =
                googleSignInManager
                    .signIn()
                    .getOrThrow()


            val authorizationResult =
                authorizeGmail()


            if (authorizationResult) {

                accountManager.saveAccount(
                    email
                )

                Result.success(
                    email
                )

            } else {

                Result.failure(
                    Exception(
                        "Gmail permission not granted"
                    )
                )
            }


        } catch (e: Exception) {

            Result.failure(e)

        }
    }


    private suspend fun authorizeGmail(): Boolean {

        return suspendCancellableCoroutine { continuation ->


            gmailAuthorizationManager.authorize(

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