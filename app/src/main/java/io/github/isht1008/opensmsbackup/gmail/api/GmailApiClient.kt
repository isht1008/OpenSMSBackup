package io.github.isht1008.opensmsbackup.gmail.api

import android.content.Context
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.ListLabelsResponse
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class GmailApiClient(
    private val context: Context,
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy()
) {

    private val serviceFactory =
        GmailServiceFactory(context)

    fun createService(
        profile: AccountProfileEntity
    ): Gmail {

        return serviceFactory.create(
            profile
        )
    }

    suspend fun listLabels(
        profile: AccountProfileEntity
    ): Result<ListLabelsResponse> {

        return withContext(Dispatchers.IO) {

            try {

                val response = retryPolicy.execute(
                    operationName = "list_labels",
                    profileId = profile.profileId
                ) {
                    createService(profile)
                        .users()
                        .labels()
                        .list("me")
                        .execute()
                }

                Result.success(response)

            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {

                return@withContext Result.failure(e)

            }

        }

    }

}
