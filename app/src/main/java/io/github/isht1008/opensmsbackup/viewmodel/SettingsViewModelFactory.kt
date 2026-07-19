package io.github.isht1008.opensmsbackup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore


class SettingsViewModelFactory(
    private val gmailAccountManager: GmailAccountManager,
    private val gmailAccountCoordinator: GmailAccountCoordinator,
    private val gmailBackupWorkCoordinator: GmailBackupWorkCoordinator,
    private val deviceProfileStore: DeviceProfileStore
) : ViewModelProvider.Factory {


    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {


        if (
            modelClass.isAssignableFrom(
                SettingsViewModel::class.java
            )
        ) {

            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                gmailAccountManager,
                gmailAccountCoordinator,
                gmailBackupWorkCoordinator,
                deviceProfileStore
            ) as T
        }


        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}
