package io.github.isht1008.opensmsbackup.gmail.credential

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager

class GoogleCredentialProvider(
    private val context: Context
) {

    fun createCredential(
        email: String
    ): GoogleAccountCredential {

        return GoogleAccountCredential
            .usingOAuth2(
                context,
                listOf(
                    GmailAuthorizationManager.GMAIL_SCOPE
                )
            )
            .apply {

                selectedAccountName = email

            }

    }

}