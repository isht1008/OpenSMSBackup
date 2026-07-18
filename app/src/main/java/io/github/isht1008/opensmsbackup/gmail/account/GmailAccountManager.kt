package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity


class GmailAccountManager(
    private val context: Context
) {

    private val accountRepository =
        MultiAccountRepository.create(
            context.applicationContext
        )

    suspend fun saveAccount(
        email: String
    ) {

        saveAccountProfile(email)
    }


    suspend fun saveAccountProfile(
        email: String
    ): AccountProfileEntity {

        return accountRepository
            .createOrReconnectAndSelect(
                accountEmail = email
            )
    }


    suspend fun prepareAccountProfile(
        email: String
    ): AccountProfileEntity {

        return accountRepository
            .createOrReconnectProfile(
                accountEmail = email
            )
    }


    suspend fun selectAccountProfile(
        profile: AccountProfileEntity
    ) {

        accountRepository.updateConnectionState(
            profileId = profile.profileId,
            connectionState =
                AccountProfileEntity
                    .CONNECTION_STATE_CONNECTED
        )

        accountRepository.selectProfile(
            profile.profileId
        )
    }


    suspend fun getAccount(): String? {

        return getSelectedAccountProfile()
            ?.accountEmail
    }


    suspend fun getSelectedAccountProfile(): AccountProfileEntity? {

        return accountRepository
            .getSelectedProfile()
    }


    suspend fun getAccountProfile(
        profileId: String
    ): AccountProfileEntity? {

        return accountRepository.getProfile(
            profileId
        )
    }


    suspend fun hasAccount(): Boolean {

        return getAccount() != null
    }


    suspend fun clearAccount() {

        accountRepository
            .disconnectSelectedProfile()
    }


    suspend fun markAuthorizationRequired(
        profile: AccountProfileEntity
    ) {

        accountRepository.updateConnectionState(
            profileId = profile.profileId,
            connectionState =
                AccountProfileEntity
                    .CONNECTION_STATE_AUTHORIZATION_REQUIRED
        )
    }
}
