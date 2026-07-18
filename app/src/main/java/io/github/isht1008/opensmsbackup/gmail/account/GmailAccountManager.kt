package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository


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

        accountRepository
            .createOrReconnectAndSelect(
                accountEmail = email
            )
    }


    suspend fun getAccount(): String? {

        return accountRepository
            .getSelectedProfile()
            ?.accountEmail
    }


    suspend fun hasAccount(): Boolean {

        return getAccount() != null
    }


    suspend fun clearAccount() {

        accountRepository
            .disconnectSelectedProfile()
    }
}
