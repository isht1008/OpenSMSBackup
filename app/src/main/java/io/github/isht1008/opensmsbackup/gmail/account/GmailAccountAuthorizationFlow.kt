package io.github.isht1008.opensmsbackup.gmail.account

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.CancellationException

interface GmailAccountAuthorizationOperations {
    suspend fun requestGmailAuthorization(profile: AccountProfileEntity)
    suspend fun markConnected(profile: AccountProfileEntity)
    suspend fun markAuthorizationRequired(profile: AccountProfileEntity)
}

class GmailAccountAuthorizationFlow(private val operations: GmailAccountAuthorizationOperations) {
    suspend fun authorize(profile: AccountProfileEntity): Result<AccountProfileEntity> = try {
        operations.markAuthorizationRequired(profile)
        operations.requestGmailAuthorization(profile)
        operations.markConnected(profile)
        Result.success(profile.copy(connectionState = AccountProfileEntity.CONNECTION_STATE_CONNECTED))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        runCatching { operations.markAuthorizationRequired(profile) }
        Result.failure(error)
    }
}
