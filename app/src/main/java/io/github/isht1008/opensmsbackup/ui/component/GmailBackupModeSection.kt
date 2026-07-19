package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeUiState

@Composable
fun GmailBackupModeSection(
    state: GmailBackupModeUiState,
    backupActive: Boolean,
    onModeSelected: (GmailBackupMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Gmail Backup Mode", style = MaterialTheme.typography.titleLarge)
        Text(
            "Saved separately for the selected Gmail account.",
            style = MaterialTheme.typography.bodyMedium
        )

        when {
            state.isLoading -> Row(
                modifier = Modifier.padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.width(12.dp))
                Text("Loading backup mode…")
            }
            state.profileId == null -> Text(
                "Select a Gmail account to choose its backup mode.",
                modifier = Modifier.padding(vertical = 12.dp)
            )
            state.mode == null -> Text(
                state.errorMessage ?: "Backup mode is unavailable.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            else -> {
                ModeCard(
                    title = "Archive (Recommended)",
                    explanation = "Keeps previous Gmail backup snapshots and adds new snapshots without deleting history.",
                    selected = state.mode == GmailBackupMode.ARCHIVE_APPEND_ONLY,
                    enabled = !backupActive && !state.isSaving,
                    onClick = { onModeSelected(GmailBackupMode.ARCHIVE_APPEND_ONLY) }
                )
                ModeCard(
                    title = "Mirror",
                    explanation = "Keeps Gmail aligned with the latest SMS state and may replace or remove older backup snapshots according to existing mirror behavior.",
                    selected = state.mode == GmailBackupMode.MIRROR,
                    enabled = !backupActive && !state.isSaving,
                    onClick = { onModeSelected(GmailBackupMode.MIRROR) }
                )
                if (state.isSaving) Text(
                    "Saving backup mode…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    explanation: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { contentDescription = "$title. $explanation" }
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
