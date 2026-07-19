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
    val mirrorConfirmationPending: Boolean = false,
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

    fun requestSelection(mode: GmailBackupMode, backupActive: Boolean): Boolean {
        if (backupActive || !state.canSelect || state.mode == mode) return false
        if (mode == GmailBackupMode.MIRROR) {
            state = state.copy(mirrorConfirmationPending = true, errorMessage = null)
            return false
        }
        return true
    }

    fun cancelMirrorConfirmation() {
        state = state.copy(mirrorConfirmationPending = false)
    }

    suspend fun confirmMirror(backupActive: Boolean): Boolean {
        if (backupActive || !state.mirrorConfirmationPending) return false
        return save(GmailBackupMode.MIRROR)
    }

    suspend fun saveArchive(backupActive: Boolean): Boolean {
        if (backupActive || !state.canSelect) return false
        return save(GmailBackupMode.ARCHIVE_APPEND_ONLY)
    }

    private suspend fun save(mode: GmailBackupMode): Boolean {
        val profileId = state.profileId ?: return false
        val previousMode = state.mode ?: return false
        val generation = ++operationGeneration
        state = state.copy(
            isSaving = true,
            mirrorConfirmationPending = false,
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
