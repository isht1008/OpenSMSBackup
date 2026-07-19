package io.github.isht1008.opensmsbackup.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.isht1008.opensmsbackup.device.DeviceProfile
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.device.DeviceDisplayName
import io.github.isht1008.opensmsbackup.device.CountryRegion
import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer

class SettingsViewModel(
    private val gmailAccountManager: GmailAccountManager,
    private val gmailAccountCoordinator: GmailAccountCoordinator,
    private val gmailBackupWorkCoordinator: GmailBackupWorkCoordinator,
    private val deviceProfileStore: DeviceProfileStore
) : ViewModel() {

    var accountProfiles by
        mutableStateOf<List<AccountProfileEntity>>(
            emptyList()
        )
        private set

    var selectedProfileId by
        mutableStateOf<String?>(null)
        private set

    var busyProfileId by
        mutableStateOf<String?>(null)
        private set

    var isAddingAccount by
        mutableStateOf(false)
        private set

    var pendingDisconnectProfile by
        mutableStateOf<AccountProfileEntity?>(null)
        private set

    var errorMessage by
        mutableStateOf<String?>(null)
        private set

    var deviceProfile by mutableStateOf<DeviceProfile?>(null)
        private set
    var deviceNameDraft by mutableStateOf("")
    var primaryPhoneDraft by mutableStateOf("")
    var secondaryPhoneDraft by mutableStateOf("")
    var defaultRegionDraft by mutableStateOf("")
    var countryError by mutableStateOf<String?>(null)
        private set
    var deviceSaveMessage by mutableStateOf<String?>(null)
        private set

    val deviceLabelPreview: String
        get() = deviceProfile?.let {
            "SMS/Devices/${DeviceDisplayName.gmailLabelSegment(deviceNameDraft, primaryPhoneDraft.ifBlank { it.primaryPhoneNumber }, it.deviceId, false)}/Conversations"
        }.orEmpty()

    val normalizedNumberExample: String
        get() = CountryRegion.validated(defaultRegionDraft)?.let { region ->
            SmsAddressNormalizer().normalize("9876543210", region).canonical
        }.orEmpty()

    val isLastUsableDisconnect: Boolean
        get() {
            val pending =
                pendingDisconnectProfile
                    ?: return false

            return pending.connectionState ==
                    AccountProfileEntity
                        .CONNECTION_STATE_CONNECTED &&
                    accountProfiles.count { profile ->
                        profile.connectionState ==
                                AccountProfileEntity
                                    .CONNECTION_STATE_CONNECTED
                    } <= 1
        }

    init {
        observeAccounts()
        refreshSelectedProfile()
        loadDeviceProfile()
    }

    fun addAccount() {
        if (isAddingAccount || busyProfileId != null) {
            return
        }

        viewModelScope.launch {
            isAddingAccount = true
            errorMessage = null

            gmailAccountCoordinator
                .connectAccount()
                .onSuccess {
                    refreshSelectedProfile()
                }
                .onFailure {
                    errorMessage =
                        "Unable to add account. Try again."
                }

            isAddingAccount = false
        }
    }

    fun selectAccount(
        profile: AccountProfileEntity
    ) {
        if (
            profile.connectionState !=
            AccountProfileEntity
                .CONNECTION_STATE_CONNECTED
        ) {
            errorMessage =
                "Authorize this account before selecting it."
            return
        }

        viewModelScope.launch {
            busyProfileId = profile.profileId
            errorMessage = null

            runCatching {
                gmailAccountManager
                    .selectAccountProfile(profile)
            }.onSuccess {
                selectedProfileId = profile.profileId
            }.onFailure {
                errorMessage =
                    "Unable to select account."
            }

            busyProfileId = null
        }
    }

    fun reauthorizeAccount(
        profile: AccountProfileEntity
    ) {
        viewModelScope.launch {
            busyProfileId = profile.profileId
            errorMessage = null

            gmailAccountCoordinator
                .authorizeAccount(profile)
                .onFailure {
                    errorMessage =
                        "Unable to authorize account. Try again."
                }

            busyProfileId = null
        }
    }

    fun requestDisconnect(
        profile: AccountProfileEntity
    ) {
        viewModelScope.launch {
            if (gmailBackupWorkCoordinator.hasActiveWork(profile.profileId)) {
                errorMessage =
                    "Cancel the running Gmail backup before disconnecting this account."
                return@launch
            }
            pendingDisconnectProfile = profile
        }
    }

    fun dismissDisconnect() {
        pendingDisconnectProfile = null
    }

    fun confirmDisconnect() {
        val profile =
            pendingDisconnectProfile
                ?: return

        pendingDisconnectProfile = null

        viewModelScope.launch {
            if (gmailBackupWorkCoordinator.hasActiveWork(profile.profileId)) {
                errorMessage =
                    "Cancel the running Gmail backup before disconnecting this account."
                return@launch
            }
            busyProfileId = profile.profileId
            errorMessage = null

            runCatching {
                gmailAccountCoordinator
                    .disconnectAccount(profile)
            }.onSuccess {
                if (selectedProfileId == profile.profileId) {
                    selectedProfileId = null
                }
            }.onFailure {
                errorMessage =
                    "Unable to disconnect account."
            }

            busyProfileId = null
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun saveDeviceProfile() {
        viewModelScope.launch {
            val current = deviceProfile ?: return@launch
            val region = CountryRegion.validated(defaultRegionDraft)
            if (region == null) {
                countryError = "Enter a supported two-letter country code, such as IN or US."
                return@launch
            }
            countryError = null
            deviceProfile = deviceProfileStore.update(
                displayName = deviceNameDraft,
                primaryPhoneNumber = primaryPhoneDraft.ifBlank { null },
                secondaryPhoneNumber = secondaryPhoneDraft.ifBlank { null },
                defaultRegion = region
            )
            deviceNameDraft = requireNotNull(deviceProfile).displayName
            defaultRegionDraft = requireNotNull(deviceProfile).defaultRegion
            primaryPhoneDraft = ""
            secondaryPhoneDraft = ""
            deviceSaveMessage = "Device profile saved. Gmail will keep the same device identity."
        }
    }

    private fun loadDeviceProfile() {
        viewModelScope.launch {
            deviceProfile = deviceProfileStore.getOrCreate()
            deviceNameDraft = requireNotNull(deviceProfile).displayName
            defaultRegionDraft = requireNotNull(deviceProfile).defaultRegion
        }
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            gmailAccountManager
                .observeAccountProfiles()
                .collectLatest { profiles ->
                    accountProfiles = profiles
                }
        }
    }

    private fun refreshSelectedProfile() {
        viewModelScope.launch {
            selectedProfileId =
                gmailAccountManager
                    .getSelectedAccountProfile()
                    ?.profileId
        }
    }
}
