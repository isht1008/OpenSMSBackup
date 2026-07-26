package io.github.isht1008.opensmsbackup.account.data

interface GmailBackupModeStore {
    suspend fun getBackupMode(profileId: String): GmailBackupMode
    suspend fun setBackupMode(profileId: String, mode: GmailBackupMode)
}

data class GmailBackupModeUiState(
    val profileId: String? = null,
    val mode: GmailBackupMode? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val policyWizardOpen: Boolean = false,
    val pendingMode: GmailBackupMode? = null,
    val errorMessage: String? = null
) {
    val canSelect: Boolean
        get() = profileId != null && mode != null && !isLoading && !isSaving
}

class GmailBackupModeController(
    private val store: GmailBackupModeStore
) {
    var state = GmailBackupModeUiState()
        private set

    private var operationGeneration = 0L

    suspend fun load(profileId: String?) {
        val generation = ++operationGeneration
        if (profileId == null) {
            state = GmailBackupModeUiState()
            return
        }
        state = GmailBackupModeUiState(profileId = profileId, isLoading = true)
        runCatching { store.getBackupMode(profileId) }
            .onSuccess { mode ->
                if (generation == operationGeneration) {
                    state = GmailBackupModeUiState(profileId = profileId, mode = mode)
                }
            }
            .onFailure {
                if (generation == operationGeneration) {
                    state = GmailBackupModeUiState(
                        profileId = profileId,
                        errorMessage = "Unable to load Gmail backup mode."
                    )
                }
            }
    }

    fun openPolicyWizard(backupActive: Boolean): Boolean {
        if (backupActive || !state.canSelect) return false
        state = state.copy(
            policyWizardOpen = true,
            pendingMode = null,
            errorMessage = null
        )
        return true
    }

    fun choosePendingMode(mode: GmailBackupMode) {
        if (!state.policyWizardOpen || state.isSaving) return
        state = state.copy(pendingMode = mode.takeIf { it != state.mode })
    }

    fun cancelPolicyWizard() {
        state = state.copy(policyWizardOpen = false, pendingMode = null)
    }

    suspend fun confirmPolicyChange(backupActive: Boolean): Boolean {
        if (backupActive || !state.policyWizardOpen) return false
        val mode = state.pendingMode ?: return false
        return save(mode)
    }

    private suspend fun save(mode: GmailBackupMode): Boolean {
        val profileId = state.profileId ?: return false
        val previousMode = state.mode ?: return false
        val generation = ++operationGeneration
        state = state.copy(
            isSaving = true,
            policyWizardOpen = false,
            pendingMode = null,
            errorMessage = null
        )
        return runCatching { store.setBackupMode(profileId, mode) }
            .fold(
                onSuccess = {
                    if (generation == operationGeneration && state.profileId == profileId) {
                        state = GmailBackupModeUiState(profileId = profileId, mode = mode)
                        true
                    } else {
                        false
                    }
                },
                onFailure = {
                    if (generation == operationGeneration && state.profileId == profileId) {
                        state = GmailBackupModeUiState(
                            profileId = profileId,
                            mode = previousMode,
                            errorMessage = "Unable to save Gmail backup mode."
                        )
                    }
                    false
                }
            )
    }
}
