package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun GmailAccountDialog(
    email: String,
    onChangeAccount: () -> Unit,
    onDisconnect: () -> Unit,
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

            androidx.compose.foundation.layout.Column {

                Text(
                    text = "Current account"
                )

                Text(
                    text = email
                )

            }

        },

        confirmButton = {

            TextButton(
                onClick = onChangeAccount
            ) {

                Text(
                    "Change Account"
                )

            }

        },

        dismissButton = {

            androidx.compose.foundation.layout.Column {

                TextButton(
                    onClick = onDisconnect
                ) {

                    Text(
                        "Disconnect"
                    )

                }

                TextButton(
                    onClick = onDismiss
                ) {

                    Text(
                        "Cancel"
                    )

                }

            }

        }

    )

}