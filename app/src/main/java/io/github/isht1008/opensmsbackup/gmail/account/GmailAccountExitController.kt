package io.github.isht1008.opensmsbackup.gmail.account

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.CancellationException

enum class GmailAccountExitAction { DISCONNECT, REVOKE }

data class GmailAccountExitUiState(
    val action: GmailAccountExitAction? = null,
    val profile: AccountProfileEntity? = null,
    val confirmationText: String = "",
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
) {
    val isOpen: Boolean get() = action != null && profile != null
    val isRevokeConfirmationValid: Boolean
        get() = confirmationText.trim().equals(REVOKE_CONFIRMATION, ignoreCase = true)

    companion object { const val REVOKE_CONFIRMATION = "REVOKE" }
}

interface GmailAccountExitOperations {
    suspend fun hasActiveBackup(profileId: String): Boolean
    suspend fun hasActiveVerification(profileId: String): Boolean
    suspend fun revokeGoogleAccess(profile: AccountProfileEntity)
    suspend fun clearCredentialSession()
    suspend fun disconnectLocally(profile: AccountProfileEntity)
    suspend fun markAuthorizationRequired(profile: AccountProfileEntity)
}

sealed interface GmailAccountExitResult {
    data class Success(val action: GmailAccountExitAction) : GmailAccountExitResult
    data class Blocked(val reason: ActiveWorkReason) : GmailAccountExitResult
    data class Failure(val googleAccessRevoked: Boolean) : GmailAccountExitResult
    data object InvalidConfirmation : GmailAccountExitResult
    data object DuplicateSubmission : GmailAccountExitResult
}

enum class ActiveWorkReason { BACKUP, VERIFICATION }

class GmailAccountExitController(private val operations: GmailAccountExitOperations) {
    var state = GmailAccountExitUiState()
        private set

    fun request(profile: AccountProfileEntity, action: GmailAccountExitAction) {
        if (!state.isProcessing) state = GmailAccountExitUiState(action = action, profile = profile)
    }

    fun updateConfirmationText(value: String) {
        if (state.action == GmailAccountExitAction.REVOKE && !state.isProcessing) {
            state = state.copy(confirmationText = value, errorMessage = null)
        }
    }

    fun cancel() {
        if (!state.isProcessing) state = GmailAccountExitUiState()
    }

    fun beginConfirmation(): GmailAccountExitResult? {
        if (state.isProcessing) return GmailAccountExitResult.DuplicateSubmission
        val action = state.action ?: return GmailAccountExitResult.InvalidConfirmation
        if (state.profile == null) return GmailAccountExitResult.InvalidConfirmation
        if (action == GmailAccountExitAction.REVOKE && !state.isRevokeConfirmationValid) {
            state = state.copy(errorMessage = "Type REVOKE exactly to continue.")
            return GmailAccountExitResult.InvalidConfirmation
        }
        state = state.copy(isProcessing = true, errorMessage = null)
        return null
    }

    suspend fun confirm(): GmailAccountExitResult {
        beginConfirmation()?.let { return it }
        return executeConfirmed()
    }

    suspend fun executeConfirmed(): GmailAccountExitResult {
        if (!state.isProcessing) return GmailAccountExitResult.InvalidConfirmation
        val action = state.action ?: return GmailAccountExitResult.InvalidConfirmation
        val profile = state.profile ?: return GmailAccountExitResult.InvalidConfirmation
        return try {
            if (operations.hasActiveBackup(profile.profileId)) return blocked(ActiveWorkReason.BACKUP)
            if (operations.hasActiveVerification(profile.profileId)) return blocked(ActiveWorkReason.VERIFICATION)

            var revoked = false
            try {
                if (action == GmailAccountExitAction.REVOKE) {
                    operations.revokeGoogleAccess(profile)
                    revoked = true
                }
                operations.clearCredentialSession()
                operations.disconnectLocally(profile)
                if (revoked) operations.markAuthorizationRequired(profile)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (revoked) runCatching { operations.markAuthorizationRequired(profile) }
                state = state.copy(
                    isProcessing = false,
                    errorMessage = when {
                        revoked -> "Google access was revoked, but local cleanup did not complete. Reconnect this account before backing up."
                        action == GmailAccountExitAction.REVOKE -> "Google access could not be revoked. The account remains connected; try again."
                        else -> "The account could not be disconnected. It remains connected; try again."
                    }
                )
                return GmailAccountExitResult.Failure(revoked)
            }

            state = GmailAccountExitUiState()
            GmailAccountExitResult.Success(action)
        } catch (cancellation: CancellationException) {
            state = state.copy(isProcessing = false)
            throw cancellation
        }
    }

    private fun blocked(reason: ActiveWorkReason): GmailAccountExitResult.Blocked {
        state = state.copy(
            isProcessing = false,
            errorMessage = when (reason) {
                ActiveWorkReason.BACKUP -> "Cancel the running Gmail backup before continuing."
                ActiveWorkReason.VERIFICATION -> "Cancel the running backup verification before continuing."
            }
        )
        return GmailAccountExitResult.Blocked(reason)
    }
}
