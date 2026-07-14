package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GmailAccountDialog(
    email: String,
    onChangeAccount: () -> Unit,
    onDisconnect: () -> Unit,
    onRevokeAccess: () -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Backup Gmail Account"
            )
        },

        text = {

            Column {

                Text(
                    text = "Connected Account",
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Divider()

            }

        },

        confirmButton = {

            Column {

                TextButton(
                    onClick = onChangeAccount
                ) {
                    Text("Change Account")
                }

                TextButton(
                    onClick = onDisconnect
                ) {
                    Text("Disconnect")
                }

                TextButton(
                    onClick = onRevokeAccess
                ) {
                    Text("Revoke Google Access")
                }

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