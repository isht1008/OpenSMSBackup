package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupScope
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupUiState
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupUiStage

data class GmailBackupSurfaceDecision(
    val showProgress: Boolean,
    val showResult: Boolean,
    val showGenericStatus: Boolean
) {
    companion object {
        fun resolve(
            gmailActive: Boolean,
            gmailTerminalAvailable: Boolean,
            localBackupActive: Boolean,
            verificationActive: Boolean
        ): GmailBackupSurfaceDecision {
            val otherActive = localBackupActive || verificationActive
            return GmailBackupSurfaceDecision(
                showProgress = gmailActive,
                showResult = !gmailActive && !otherActive && gmailTerminalAvailable,
                showGenericStatus = !gmailActive && (otherActive || !gmailTerminalAvailable)
            )
        }
    }
}

data class GmailBackupResultPresentation(
    val title: String,
    val typeAndMode: String,
    val durationLine: String,
    val outcomeLines: List<String>,
    val details: List<Pair<String, String>>,
    val limitedNotice: String?
)

fun GmailBackupUiState.hasTerminalResult(): Boolean =
    workId != null && stage in setOf(
        GmailBackupUiStage.COMPLETED,
        GmailBackupUiStage.COMPLETED_WITH_FAILURES,
        GmailBackupUiStage.CANCELLED,
        GmailBackupUiStage.ABORTED,
        GmailBackupUiStage.FAILED
    )

fun maskGmailAccount(email: String?): String? {
    val value = email?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val at = value.indexOf('@')
    if (at <= 0 || at == value.lastIndex) return "Hidden account"
    return value.first() + "***" + value.substring(at)
}

fun GmailBackupScope.displayLabel(): String = when (this) {
    GmailBackupScope.INCREMENTAL -> "Incremental"
    GmailBackupScope.FULL -> "Full reconciliation"
    GmailBackupScope.RECENT_TEST -> "Recent-10 test"
}

fun GmailBackupMode?.displayLabel(): String = when (this) {
    GmailBackupMode.ARCHIVE_APPEND_ONLY -> "Archive"
    GmailBackupMode.MIRROR -> "Mirror"
    null -> "Mode unavailable"
}

fun GmailBackupCompletionState.displayLabel(): String = when (this) {
    GmailBackupCompletionState.COMPLETED -> "Completed"
    GmailBackupCompletionState.COMPLETED_WITH_FAILURES -> "Completed with failures"
    GmailBackupCompletionState.LIMITED_TEST_COMPLETED -> "Recent-10 test completed"
    GmailBackupCompletionState.CANCELLED -> "Cancelled"
    GmailBackupCompletionState.ABORTED_FATAL -> "Stopped"
    GmailBackupCompletionState.ABORTED_REPEATED_FAILURES -> "Stopped after repeated failures"
    GmailBackupCompletionState.FAILED_BEFORE_START -> "Failed before starting"
}

fun GmailBackupCompletion.toPresentation(): GmailBackupResultPresentation {
    val successful = state == GmailBackupCompletionState.COMPLETED ||
        state == GmailBackupCompletionState.LIMITED_TEST_COMPLETED
    val duration = formatGmailDuration(durationMillis)
    val outcome = mutableListOf<String>()
    if (successful && uploaded == 0) {
        outcome += "No new conversations to upload"
    } else if (successful) {
        outcome += uploaded.toString() + " " + conversationWord(uploaded) + " uploaded"
        if (locallyUnchanged > 0) outcome += locallyUnchanged.toString() + " unchanged"
    } else {
        outcome += uploaded.toString() + " uploaded · " + failed + " failed · " + remaining + " remaining"
    }
    return GmailBackupResultPresentation(
        title = if (successful) "Gmail backup complete" else "Gmail backup incomplete",
        typeAndMode = backupScope.displayLabel() + " · " + backupMode.displayLabel(),
        durationLine = if (successful) "Completed in $duration" else "Stopped after $duration",
        outcomeLines = listOf(checked.toString() + " " + conversationWord(checked) + " checked") + outcome,
        details = listOf(
            "Backup type" to backupScope.displayLabel(),
            "Mode" to backupMode.displayLabel(),
            "Account" to (maskGmailAccount(accountEmail) ?: "Unavailable"),
            "Source conversations" to sourceConversationTotal.toString(),
            "Source messages" to sourceMessageTotal.toString(),
            "Checked" to checked.toString(),
            "Locally unchanged" to locallyUnchanged.toString(),
            "Legacy initialized locally" to legacyLocallyInitialized.toString(),
            "Checked remotely" to remotelyCompared.toString(),
            "Remote check - no update" to remotelyUnchanged.toString(),
            "Uploaded" to uploaded.toString(),
            "Recoveries" to remoteRecoveries.toString(),
            "Failed" to failed.toString(),
            "Remaining" to remaining.toString(),
            "Completion state" to state.displayLabel(),
            "Index recovery used" to if (gmailIndexUsed) "Yes" else "No",
            "Total duration" to duration
        ),
        limitedNotice = if (backupScope == GmailBackupScope.RECENT_TEST || isLimitedTest) "Not a full backup" else null
    )
}

@Composable
fun GmailBackupResultCard(completion: GmailBackupCompletion) {
    var showDetails by rememberSaveable(completion.profileId, completion.durationMillis) {
        mutableStateOf(false)
    }
    val presentation = completion.toPresentation()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(presentation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(presentation.typeAndMode)
            Text(presentation.durationLine)
            Spacer(Modifier.height(8.dp))
            presentation.outcomeLines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            presentation.limitedNotice?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide details" else "Show details")
            }
            if (showDetails) {
                presentation.details.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        Text(value, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun GmailBackupFallbackResultCard(state: GmailBackupUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Gmail backup incomplete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Stopped after " + formatGmailDuration(state.elapsedMillis))
            Text(state.uploaded.toString() + " uploaded · " + state.failed + " failed · " + state.remaining + " remaining")
        }
    }
}

@Composable
fun GmailBackupProgressCard(
    state: GmailBackupUiState,
    cancellationRequested: Boolean,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Gmail backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.fraction?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(state.checked.toString() + " / " + state.total + " checked")
            Text("Elapsed " + formatGmailDuration(state.elapsedMillis))
            Text("ETA " + (state.approximateEtaSeconds?.let { formatGmailDuration(it * 1_000L) } ?: "Calculating"))
            Text("Locally unchanged: " + state.locallyUnchanged)
            if (state.legacyLocallyInitialized > 0) Text("Legacy initialized locally: " + state.legacyLocallyInitialized)
            Text("Checked remotely: " + state.remotelyCompared)
            Text("Uploaded: " + state.uploaded)
            Text("Failed: " + state.failed)
            Text("Status: " + state.phase)
            TextButton(onClick = onCancel, enabled = state.isCancellable) {
                Text(if (cancellationRequested) "Cancelling…" else "Cancel")
            }
        }
    }
}

private fun conversationWord(count: Int) = if (count == 1) "conversation" else "conversations"

fun formatGmailDuration(millis: Long): String {
    val seconds = (millis / 1_000L).coerceAtLeast(0L)
    return when {
        seconds < 60L -> seconds.toString() + "s"
        seconds < 3_600L -> (seconds / 60L).toString() + "m " + (seconds % 60L) + "s"
        else -> (seconds / 3_600L).toString() + "h " + ((seconds % 3_600L) / 60L) + "m"
    }
}
