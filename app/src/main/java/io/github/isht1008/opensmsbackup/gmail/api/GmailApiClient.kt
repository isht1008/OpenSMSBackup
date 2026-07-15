package io.github.isht1008.opensmsbackup.gmail.api

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import io.github.isht1008.opensmsbackup.gmail.credential.GoogleCredentialProvider
import com.google.api.services.gmail.model.ListLabelsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GmailApiClient(
    private val context: Context
) {

    private val credentialProvider =
        GoogleCredentialProvider(context)

    fun createService(
        email: String
    ): Gmail {

        val credential =
            credentialProvider.createCredential(
                email
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

    suspend fun listLabels(
        email: String
    ): Result<ListLabelsResponse> {

        return withContext(Dispatchers.IO) {

            try {

                val response =
                    createService(email)
                        .users()
                        .labels()
                        .list("me")
                        .execute()

                Result.success(response)

            } catch (e: Exception) {

                return@withContext Result.failure(e)

            }

        }

    }

}