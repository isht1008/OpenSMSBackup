package io.github.isht1008.opensmsbackup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.isht1008.opensmsbackup.account.data.AccountManagementRepository
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeStore
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitOperations
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorCoordinator
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator

class SettingsViewModelFactory(
    private val gmailAccountManager: GmailAccountManager,
    private val gmailAccountCoordinator: GmailAccountCoordinator,
    private val gmailBackupWorkCoordinator: GmailBackupWorkCoordinator,
    private val deviceProfileStore: DeviceProfileStore,
    private val accountManagementRepository: AccountManagementRepository,
    private val backupModeStore: GmailBackupModeStore,
    private val accountExitOperations: GmailAccountExitOperations,
    private val fullMirrorCoordinator: FullMirrorCoordinator
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                gmailAccountManager,
                gmailAccountCoordinator,
                gmailBackupWorkCoordinator,
                deviceProfileStore,
                accountManagementRepository,
                backupModeStore,
                accountExitOperations,
                fullMirrorCoordinator::hasActiveForProfile
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
