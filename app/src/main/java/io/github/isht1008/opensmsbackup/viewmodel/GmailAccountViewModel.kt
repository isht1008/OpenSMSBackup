package io.github.isht1008.opensmsbackup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GmailAccountViewModel(
    private val accountManager: GmailAccountManager
) : ViewModel() {


    private val _gmailAccount =
        MutableStateFlow<String?>(null)

    val gmailAccount: StateFlow<String?> =
        _gmailAccount


    init {
        loadAccount()
    }


    private fun loadAccount() {

        viewModelScope.launch {

            _gmailAccount.value =
                accountManager.getAccount()
        }
    }


    fun saveAccount(
        email: String
    ) {

        viewModelScope.launch {

            accountManager.saveAccount(email)

            _gmailAccount.value =
                email
        }
    }


    fun removeAccount() {

        viewModelScope.launch {

            accountManager.clearAccount()

            _gmailAccount.value =
                null
        }
    }
}