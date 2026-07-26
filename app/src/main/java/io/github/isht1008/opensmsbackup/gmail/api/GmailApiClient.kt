package io.github.isht1008.opensmsbackup.gmail.api

import android.content.Context
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.ListLabelsResponse
import com.google.api.services.gmail.model.Profile
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

    /** Read-only connectivity check. It does not list or fetch mailbox messages. */
    suspend fun getProfile(profile: AccountProfileEntity): Result<Profile> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(
                    retryPolicy.execute(
                        operationName = "get_profile",
                        profileId = profile.profileId
                    ) {
                        createService(profile).users().getProfile("me").execute()
                    }
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

}
