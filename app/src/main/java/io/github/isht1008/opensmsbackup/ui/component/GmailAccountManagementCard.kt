package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeUiState
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import io.github.isht1008.opensmsbackup.ui.screen.maskGmailAccount
import java.text.DateFormat
import java.util.Date

@Composable
fun GmailAccountManagementCard(
    profile: AccountProfileEntity?,
    policyState: GmailBackupModeUiState,
    lastBackupTime: Long?,
    verification: BackupVerificationEntity?,
    gmailBackupActive: Boolean,
    allBackupActionsBusy: Boolean,
    onIncrementalBackup: () -> Unit,
    onFullBackup: () -> Unit,
    onRecentTestBackup: () -> Unit,
    onHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showFullBackupConfirmation by remember(profile?.profileId) {
        mutableStateOf(false)
    }

    if (showFullBackupConfirmation && profile != null) {
        AlertDialog(
            onDismissRequest = { showFullBackupConfirmation = false },
            title = { Text("Start Full Archive Backup?") },
            text = {
                Column {
                    AccountValue("Selected Account", maskGmailAccount(profile.accountEmail) ?: "Account")
                    AccountValue("Backup Mode", "Archive")
                    Text(
                        "Every local SMS conversation and the complete Gmail Archive namespace " +
                            "will be checked. Conversation and " +
                            "message totals will be calculated once by the backup worker. " +
                            "Full Archive backup remains experimental while large-device " +
                            "performance is physically validated. Existing Archive snapshots " +
                            "are indexed before uploads begin. This operation may take time " +
                            "and use substantial network or mobile data.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFullBackupConfirmation = false
                        onFullBackup()
                    }
                ) {
                    Text("Start Full Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullBackupConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Gmail Account", style = MaterialTheme.typography.titleLarge)
            if (profile == null) {
                Text(
                    "No Gmail account selected. To enable Gmail backup, go to Settings and add an account.",
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                PrimaryButton("Open Settings", onOpenSettings)
                return@Column
            }

            AccountValue("Account", maskGmailAccount(profile.accountEmail) ?: "Account")
            AccountValue("Connection", connectionLabel(profile.connectionState))
            AccountValue("Current Backup Policy", policyLabel(policyState.mode, policyState.isLoading))
            AccountValue(
                "Last Backup",
                lastBackupTime?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "No completed Gmail backup"
            )
            AccountValue("Backup Health", healthLabel(verification))

            if (policyState.mode == GmailBackupMode.ARCHIVE_APPEND_ONLY) {
                Spacer(Modifier.padding(top = 4.dp))
                PrimaryButton(
                    "Backup Now",
                    onIncrementalBackup,
                    enabled = !allBackupActionsBusy
                )
            }
            Spacer(Modifier.padding(top = 4.dp))
            PrimaryButton(
                if (gmailBackupActive) "Backing up to Gmail…" else if (policyState.mode == GmailBackupMode.MIRROR) "Full Mirror Preview" else "Reconcile Full Backup",
                {
                    if (policyState.mode == GmailBackupMode.ARCHIVE_APPEND_ONLY) {
                        showFullBackupConfirmation = true
                    } else {
                        onFullBackup()
                    }
                },
                enabled = !allBackupActionsBusy
            )
            Text(
                if (policyState.mode == GmailBackupMode.MIRROR) {
                    "Creates a read-only, account-specific Full Mirror preview first. " +
                        "Your Archive account and its Gmail backups are not affected."
                } else {
                    "Reconciliation checks every conversation and the complete Gmail archive. " +
                        "It can take significantly longer."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.padding(top = 4.dp))
            PrimaryButton(
                "Test Backup · Recent 10",
                onRecentTestBackup,
                enabled = !allBackupActionsBusy
            )
            Text(
                "Includes only the 10 most recently active conversations. " +
                    "Omitted conversations remain untouched.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.padding(top = 4.dp))
            PrimaryButton("Backup History", onHistory)
            policyState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun AccountValue(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun BackupPolicyWizard(
    state: GmailBackupModeUiState,
    backupActive: Boolean,
    onChoose: (GmailBackupMode) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (!state.policyWizardOpen) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Change Backup Policy") },
        text = {
            Column {
                AccountValue("Current Policy", policyLabel(state.mode, false))
                AccountValue("New Policy", state.pendingMode?.let(::policyLabel) ?: "Select a policy")
                PolicyChoice(
                    "Archive (Recommended)",
                    "Preserves backup history and adds new snapshots.",
                    state.pendingMode == GmailBackupMode.ARCHIVE_APPEND_ONLY,
                    !backupActive && !state.isSaving
                ) { onChoose(GmailBackupMode.ARCHIVE_APPEND_ONLY) }
                PolicyChoice(
                    "Mirror (Advanced)",
                    "Keeps Gmail aligned with the latest SMS state using the existing mirror replacement behavior.",
                    state.pendingMode == GmailBackupMode.MIRROR,
                    !backupActive && !state.isSaving
                ) { onChoose(GmailBackupMode.MIRROR) }
                Text(
                    "Existing backups will be preserved. Future backups will use the new policy. " +
                        "Existing Gmail backups will not be modified automatically. " +
                        "You can change the policy again later.",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.pendingMode != null && !backupActive && !state.isSaving
            ) { Text("Confirm Policy Change") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun PolicyChoice(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected, null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun connectionLabel(state: String): String = when (state) {
    AccountProfileEntity.CONNECTION_STATE_CONNECTED -> "Connected"
    AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED -> "Authorization required"
    else -> "Disconnected"
}

private fun policyLabel(mode: GmailBackupMode?, loading: Boolean = false): String = when {
    loading -> "Loading…"
    mode == GmailBackupMode.ARCHIVE_APPEND_ONLY -> "Archive"
    mode == GmailBackupMode.MIRROR -> "Mirror"
    else -> "Unavailable"
}

private fun healthLabel(result: BackupVerificationEntity?): String = when (result?.status) {
    "VERIFIED" -> "Verified · ${result.verificationPercent.toInt()}% represented"
    "PARTIALLY_VERIFIED" -> "Needs attention · ${result.verificationPercent.toInt()}% represented"
    "FAILED" -> "Verification failed · ${result.missingMessageCount} missing"
    "CANCELLED" -> "Verification cancelled"
    "NOT_AVAILABLE" -> "Not available"
    else -> "Not verified yet"
}
