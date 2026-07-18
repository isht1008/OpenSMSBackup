package io.github.isht1008.opensmsbackup.gmail.credential

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager

class GoogleCredentialProvider(
    private val context: Context
) {

    fun createCredential(
        profile: AccountProfileEntity
    ): GoogleAccountCredential {

        require(profile.accountEmail.isNotBlank()) {
            "Account profile email cannot be blank."
        }

        return GoogleAccountCredential
            .usingOAuth2(
                context,
                listOf(
                    GmailAuthorizationManager.GMAIL_SCOPE
                )
            )
            .apply {

                selectedAccountName =
                    profile.accountEmail

            }

    }

}
