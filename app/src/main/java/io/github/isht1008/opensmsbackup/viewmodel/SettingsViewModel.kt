package io.github.isht1008.opensmsbackup.viewmodel

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationResolutionRequiredException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.isht1008.opensmsbackup.device.DeviceProfile
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.device.DeviceDisplayName
import io.github.isht1008.opensmsbackup.device.CountryRegion
import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer
import io.github.isht1008.opensmsbackup.account.data.AccountManagementItem
import io.github.isht1008.opensmsbackup.account.data.AccountManagementRepository
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeController
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeStore
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeUiState
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitAction
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitController
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitOperations
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitResult
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitUiState

class SettingsViewModel(
    private val gmailAccountManager: GmailAccountManager,
    private val gmailAccountCoordinator: GmailAccountCoordinator,
    private val gmailBackupWorkCoordinator: GmailBackupWorkCoordinator,
    private val deviceProfileStore: DeviceProfileStore,
    private val accountManagementRepository: AccountManagementRepository,
    backupModeStore: GmailBackupModeStore,
    accountExitOperations: GmailAccountExitOperations
) : ViewModel() {

    private val backupModeController = GmailBackupModeController(backupModeStore)
    private val accountExitController = GmailAccountExitController(accountExitOperations)

    var accountProfiles by
        mutableStateOf<List<AccountProfileEntity>>(
            emptyList()
        )
        private set

    var accountManagementItems by mutableStateOf<List<AccountManagementItem>>(emptyList())
        private set

    var backupPolicyUiState by mutableStateOf(GmailBackupModeUiState())
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

    var gmailAccountExitState by mutableStateOf(GmailAccountExitUiState())
        private set

    var errorMessage by
        mutableStateOf<String?>(null)
        private set

    var onAuthorizationConsentRequired: ((PendingIntent) -> Unit)? = null
    private var pendingAuthorizationProfileId: String? = null
    private var selectAfterPendingAuthorization = false

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
                .onFailure { handleAuthorizationFailure(it, "Unable to add account. Try again.") }

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
                .onSuccess {
                    if (selectedProfileId == profile.profileId) refreshSelectedProfile()
                }
                .onFailure {
                    handleAuthorizationFailure(it, "Unable to authorize account. Try again.")
                }

            busyProfileId = null
        }
    }

    fun openBackupPolicy(item: AccountManagementItem) {
        if (busyProfileId != null) return
        viewModelScope.launch {
            busyProfileId = item.profile.profileId
            backupModeController.load(item.profile.profileId)
            backupModeController.openPolicyWizard(
                gmailBackupWorkCoordinator.hasActiveWork(item.profile.profileId)
            )
            backupPolicyUiState = backupModeController.state
            backupPolicyUiState.errorMessage?.let { errorMessage = it }
            busyProfileId = null
        }
    }

    fun chooseBackupPolicy(mode: GmailBackupMode) {
        backupModeController.choosePendingMode(mode)
        backupPolicyUiState = backupModeController.state
    }

    fun cancelBackupPolicy() {
        backupModeController.cancelPolicyWizard()
        backupPolicyUiState = backupModeController.state
    }

    fun confirmBackupPolicy() {
        val profileId = backupPolicyUiState.profileId ?: return
        if (backupPolicyUiState.isSaving || backupPolicyUiState.pendingMode == null) return
        viewModelScope.launch {
            val backupActive = gmailBackupWorkCoordinator.hasActiveWork(profileId)
            val saved = backupModeController.confirmPolicyChange(backupActive)
            backupPolicyUiState = backupModeController.state
            errorMessage = when {
                saved -> null
                backupActive -> "Cancel the running Gmail backup before changing its policy."
                else -> backupModeController.state.errorMessage ?: "Unable to change backup policy."
            }
        }
    }

    fun completeAuthorization(resultIntent: Intent?) {
        val profileId = pendingAuthorizationProfileId ?: return
        val selectAfter = selectAfterPendingAuthorization
        pendingAuthorizationProfileId = null
        selectAfterPendingAuthorization = false
        if (resultIntent == null) {
            errorMessage = "Gmail authorization was cancelled. The account remains unauthorized."
            return
        }
        viewModelScope.launch {
            busyProfileId = profileId
            gmailAccountCoordinator.completeAuthorization(profileId, resultIntent, selectAfter)
                .onSuccess { if (selectAfter) selectedProfileId = profileId }
                .onFailure { errorMessage = "Unable to complete Gmail authorization. Try again." }
            busyProfileId = null
        }
    }

    fun cancelPendingAuthorization() {
        pendingAuthorizationProfileId = null
        selectAfterPendingAuthorization = false
        errorMessage = "Gmail authorization was cancelled. The account remains unauthorized."
    }

    private fun handleAuthorizationFailure(error: Throwable, fallback: String) {
        val resolution = error as? GmailAuthorizationResolutionRequiredException
        if (resolution == null) {
            errorMessage = fallback
            return
        }
        pendingAuthorizationProfileId = resolution.profileId
        selectAfterPendingAuthorization = resolution.selectAfterAuthorization
        errorMessage = null
        onAuthorizationConsentRequired?.invoke(resolution.resolution)
    }

    fun requestDisconnect(profile: AccountProfileEntity) {
        accountExitController.request(profile, GmailAccountExitAction.DISCONNECT)
        gmailAccountExitState = accountExitController.state
    }

    fun requestRevoke(profile: AccountProfileEntity) {
        accountExitController.request(profile, GmailAccountExitAction.REVOKE)
        gmailAccountExitState = accountExitController.state
    }

    fun updateRevokeConfirmation(value: String) {
        accountExitController.updateConfirmationText(value)
        gmailAccountExitState = accountExitController.state
    }

    fun cancelAccountExit() {
        accountExitController.cancel()
        gmailAccountExitState = accountExitController.state
    }

    fun confirmAccountExit() {
        if (accountExitController.beginConfirmation() != null) {
            gmailAccountExitState = accountExitController.state
            return
        }
        gmailAccountExitState = accountExitController.state
        viewModelScope.launch {
            val profileId = accountExitController.state.profile?.profileId
            busyProfileId = profileId
            when (val result = accountExitController.executeConfirmed()) {
                is GmailAccountExitResult.Success -> {
                    if (selectedProfileId == profileId) refreshSelectedProfile()
                    errorMessage = if (result.action == GmailAccountExitAction.REVOKE) {
                        "Google access revoked and account disconnected. Existing Gmail backups were not changed."
                    } else {
                        "Account disconnected locally. Google access and existing Gmail backups were not changed."
                    }
                }
                is GmailAccountExitResult.Failure -> {
                    if (result.googleAccessRevoked) refreshSelectedProfile()
                    errorMessage = accountExitController.state.errorMessage
                }
                is GmailAccountExitResult.Blocked,
                GmailAccountExitResult.InvalidConfirmation,
                GmailAccountExitResult.DuplicateSubmission ->
                    errorMessage = accountExitController.state.errorMessage
            }
            gmailAccountExitState = accountExitController.state
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
            accountManagementRepository.observeItems()
                .collectLatest { items ->
                    accountManagementItems = items
                    accountProfiles = items.map(AccountManagementItem::profile)
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
