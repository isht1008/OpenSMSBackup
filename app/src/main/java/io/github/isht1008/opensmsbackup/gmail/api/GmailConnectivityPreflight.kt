package io.github.isht1008.opensmsbackup.gmail.api

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity

fun interface GmailConnectivityGateway {
    suspend fun check(profile: AccountProfileEntity): Result<Unit>
}

class GmailConnectivityPreflight(
    private val gateway: GmailConnectivityGateway
) {
    suspend fun check(profile: AccountProfileEntity): Result<Unit> = gateway.check(profile)
}

class AndroidGmailConnectivityGateway(
    private val client: GmailApiClient
) : GmailConnectivityGateway {
    override suspend fun check(profile: AccountProfileEntity): Result<Unit> =
        client.getProfile(profile).map { Unit }
}
