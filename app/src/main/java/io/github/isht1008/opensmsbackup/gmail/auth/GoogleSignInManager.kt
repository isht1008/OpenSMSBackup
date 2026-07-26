package io.github.isht1008.opensmsbackup.gmail.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.isht1008.opensmsbackup.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class GoogleSignInManager(
    private val context: Context
) {

    private val credentialManager =
        CredentialManager.create(context)


    suspend fun signIn(): Result<String> {

        return withContext(Dispatchers.Main) {

            try {

                val googleIdOption =
                    GetSignInWithGoogleOption.Builder(
                        BuildConfig.GOOGLE_SERVER_CLIENT_ID
                    )
                        .build()


                val request =
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            googleIdOption
                        )
                        .build()


                val response =
                    credentialManager.getCredential(
                        request = request,
                        context = context
                    )


                val googleCredential =
                    GoogleIdTokenCredential
                        .createFrom(
                            response.credential.data
                        )


                Result.success(
                    googleCredential.email
                        ?: throw IllegalStateException(
                            "Google account email not available"
                        )
                )


            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: GetCredentialException) {

                Result.failure(
                    Exception(
                        "Credential error: ${e.message}",
                        e
                    )
                )

            } catch (e: Exception) {

                Result.failure(e)
            }
        }
    }

    suspend fun clearCredentialState() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
