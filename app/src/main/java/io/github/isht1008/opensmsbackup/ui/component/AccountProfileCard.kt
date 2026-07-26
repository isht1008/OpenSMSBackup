package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountProfileCard(
    profile: AccountProfileEntity,
    policy: GmailBackupMode,
    lastBackupTime: Long?,
    verification: BackupVerificationEntity?,
    isSelected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onAuthorize: () -> Unit,
    onChangePolicy: () -> Unit,
    onDisconnect: () -> Unit,
    onRevoke: () -> Unit
) {
    val status =
        when (profile.connectionState) {
            AccountProfileEntity
                .CONNECTION_STATE_CONNECTED ->
                "Connected"

            AccountProfileEntity
                .CONNECTION_STATE_AUTHORIZATION_REQUIRED ->
                "Authorization Required"

            AccountProfileEntity
                .CONNECTION_STATE_DISCONNECTED ->
                "Disconnected"

            else -> profile.connectionState
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = profile.accountEmail,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Policy: ${if (policy == GmailBackupMode.ARCHIVE_APPEND_ONLY) "Archive" else "Mirror"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Last backup: ${lastBackupTime?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Not available"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Backup health: ${verificationHealth(verification)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text =
                    if (isSelected) {
                        "$status • Selected account"
                    } else {
                        status
                    },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (
                        profile.connectionState ==
                        AccountProfileEntity
                            .CONNECTION_STATE_AUTHORIZATION_REQUIRED
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    enabled = !isBusy,
                    onClick = onChangePolicy
                ) {
                    Text("Change Policy")
                }
                when (profile.connectionState) {
                    AccountProfileEntity
                        .CONNECTION_STATE_CONNECTED -> {
                        if (!isSelected) {
                            TextButton(
                                enabled = !isBusy,
                                onClick = onSelect
                            ) {
                                Text("Select")
                            }
                        }

                        TextButton(
                            enabled = !isBusy,
                            onClick = onDisconnect
                        ) {
                            Text("Disconnect Account")
                        }
                        TextButton(
                            enabled = !isBusy,
                            onClick = onRevoke
                        ) {
                            Text("Disconnect and Revoke Google Access")
                        }
                    }

                    AccountProfileEntity
                        .CONNECTION_STATE_AUTHORIZATION_REQUIRED,
                    AccountProfileEntity
                        .CONNECTION_STATE_DISCONNECTED -> {
                        TextButton(
                            enabled = !isBusy,
                            onClick = onAuthorize
                        ) {
                            Text("Re-authorize")
                        }
                    }
                }
            }
        }
    }
}

private fun verificationHealth(value: BackupVerificationEntity?): String = when (value?.status) {
    "VERIFIED" -> "Verified"
    "PARTIALLY_VERIFIED" -> "Needs attention"
    "FAILED" -> "Failed"
    "CANCELLED" -> "Cancelled"
    "NOT_AVAILABLE" -> "Not available"
    else -> "Not verified"
}
