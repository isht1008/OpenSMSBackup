package io.github.isht1008.opensmsbackup.gmail.api

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.credential.GoogleCredentialProvider

class GmailServiceFactory(
    private val credentialProvider: GoogleCredentialProvider
) {

    constructor(context: Context) : this(
        GoogleCredentialProvider(
            context.applicationContext
        )
    )

    fun create(
        profile: AccountProfileEntity
    ): Gmail {

        require(profile.accountEmail.isNotBlank()) {
            "Account profile email cannot be blank."
        }

        require(
            profile.connectionState ==
                    AccountProfileEntity
                        .CONNECTION_STATE_CONNECTED
        ) {
            "Cannot create a Gmail service for a profile that is not connected."
        }

        val credential =
            credentialProvider.createCredential(
                profile
            )

        return Gmail.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(
                "OpenSMSBackup"
            )
            .build()
    }
}
