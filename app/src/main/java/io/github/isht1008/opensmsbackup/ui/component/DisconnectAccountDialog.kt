package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity

@Composable
fun DisconnectAccountDialog(
    profile: AccountProfileEntity,
    isLastUsableAccount: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Disconnect account?")
        },
        text = {
            Text(
                if (isLastUsableAccount) {
                    "This is the last connected account. Backups will be unavailable until an account is connected again. No Gmail backup data will be deleted."
                } else {
                    "This account will be disconnected from this device. No Gmail backup data will be deleted."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
