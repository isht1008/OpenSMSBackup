package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitAction
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountExitUiState

@Composable
fun GmailAccountExitDialog(
    state: GmailAccountExitUiState,
    onConfirmationChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val profile = state.profile ?: return
    when (state.action) {
        GmailAccountExitAction.DISCONNECT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Disconnect account?") },
            text = {
                Column {
                    Text(
                        "Future backups for this account will stop. The account will be disconnected locally, " +
                            "but Google authorization will not be revoked. Existing Gmail backups remain unchanged, " +
                            "and you can reconnect the account later."
                    )
                    ExitError(state.errorMessage)
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !state.isProcessing) {
                    Text(if (state.isProcessing) "Disconnecting…" else "Disconnect Account")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !state.isProcessing) { Text("Cancel") }
            }
        )

        GmailAccountExitAction.REVOKE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Revoke Google access?") },
            text = {
                Column {
                    Text("Account: ${profile.accountEmail}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Future backups will stop. OpenSMSBackup Gmail authorization will be revoked, and " +
                            "reconnection will require Google consent again. Existing Gmail backup emails and " +
                            "labels are not deleted. Local backup and verification history is preserved.",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text("Type REVOKE to continue.", modifier = Modifier.padding(top = 16.dp))
                    OutlinedTextField(
                        value = state.confirmationText,
                        onValueChange = onConfirmationChanged,
                        enabled = !state.isProcessing,
                        singleLine = true,
                        label = { Text("Confirmation") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    ExitError(state.errorMessage)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    enabled = state.isRevokeConfirmationValid && !state.isProcessing
                ) { Text(if (state.isProcessing) "Revoking…" else "Revoke Access") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !state.isProcessing) { Text("Cancel") }
            }
        )

        null -> Unit
    }
}

@Composable
private fun ExitError(message: String?) {
    message?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
    }
}
