package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorAction
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorPreview
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorFailureCategory
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorPreviewPresentation
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorWorkProgress
import io.github.isht1008.opensmsbackup.gmail.mirror.FullMirrorWorkResult

@Composable
fun FullMirrorPreviewDialog(
    preview: FullMirrorPreview,
    error: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by rememberSaveable(preview.binding.runId) { mutableStateOf("") }
    val trash = preview.trashCount
    val presentation = FullMirrorPreviewPresentation.resolve(preview)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title) },
        text = {
            Column {
                Text("Mirror account: ${maskGmailAccount(preview.binding.accountIdentity)}")
                Text("Archive account: Not affected", style = MaterialTheme.typography.titleSmall)
                Text("This action applies only to the masked Mirror account. Your Archive account and its Gmail backups will not be changed.", modifier = Modifier.padding(vertical = 8.dp))
                PreviewCount("Local conversations", preview.localConversations)
                PreviewCount("Owned remote conversations", preview.ownedRemoteConversations)
                PreviewCount("Unchanged", preview.count(FullMirrorAction.UNCHANGED))
                PreviewCount("New uploads", preview.count(FullMirrorAction.UPLOAD_NEW))
                PreviewCount("Changed replacements", preview.count(FullMirrorAction.REPLACE_CHANGED))
                PreviewCount("Remote-only proposed for Trash", preview.count(FullMirrorAction.TRASH_REMOTE_ONLY))
                PreviewCount("Cache recoveries", preview.count(FullMirrorAction.RECOVER_CACHE))
                PreviewCount("Conflicts", preview.conflicts)
                PreviewCount("Foreign/unowned ignored", preview.foreignIgnored)
                PreviewCount("Unreadable/failed", preview.failed)
                PreviewCount("Estimated Gmail reads", preview.ownedRemoteConversations)
                PreviewCount("Estimated uploads", preview.count(FullMirrorAction.UPLOAD_NEW) + preview.count(FullMirrorAction.REPLACE_CHANGED))
                PreviewCount("Estimated recoverable Trash moves", trash)
                if (preview.highRisk) Text("High risk: Trash volume exceeds the safety threshold.", color = MaterialTheme.colorScheme.error)
                if (!preview.executionAllowed) {
                    Text("Execution is blocked. No Gmail changes can be confirmed from this preview.", color = MaterialTheme.colorScheme.error)
                    FullMirrorFailureCategory.entries.filter { preview.reasonCount(it) > 0 && it != FullMirrorFailureCategory.NONE }
                        .forEach { reason -> PreviewCount(reason.name.lowercase().replace('_', ' '), preview.reasonCount(reason)) }
                }
                if (presentation.showTypedConfirmation) {
                    Text("Gmail Trash is recoverable. Type MIRROR $trash to approve these moves.", modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Typed confirmation") })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (presentation.showConfirmationAction) onConfirm(typed.takeIf { trash > 0 }) else onDismiss()
            }) { Text(presentation.primaryActionLabel) }
        },
        dismissButton = if (presentation.showConfirmationAction) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null
    )
}

@Composable private fun PreviewCount(label: String, value: Int) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text("%,d".format(value)) }
}

@Composable
fun FullMirrorProgressCard(progress: FullMirrorWorkProgress, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Full Mirror", style = MaterialTheme.typography.titleLarge)
            Text("Preview-approved")
            if (progress.maskedAccount.isNotBlank()) Text("Mirror account: ${progress.maskedAccount}")
            Text("${progress.checked} / ${progress.total} actions checked")
            PreviewCount("Unchanged", progress.unchanged)
            PreviewCount("New uploaded", progress.newUploaded)
            PreviewCount("Changed replaced", progress.changedReplaced)
            PreviewCount("Previous snapshots Trashed", progress.previousTrashed)
            PreviewCount("Remote-only snapshots Trashed", progress.remoteOnlyTrashed)
            PreviewCount("Recoveries", progress.recoveries)
            PreviewCount("Conflicts", progress.conflicts)
            PreviewCount("Warnings", progress.warnings)
            PreviewCount("Failed", progress.failed)
            PreviewCount("Remaining", progress.remaining)
            Text("Elapsed ${progress.elapsedMillis / 1000}s")
            Text("ETA ${progress.etaSeconds?.let { "${it}s" } ?: "Calculating"}")
            Text("Status: ${progress.phase}")
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}



@Composable
fun FullMirrorResultCard(result: FullMirrorWorkResult, onResume: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (result.state == "COMPLETED") "Full Mirror complete" else "Full Mirror incomplete",
                style = MaterialTheme.typography.titleLarge
            )
            Text("Completed in ${result.durationMillis / 1000}s")
            if (result.maskedAccount.isNotBlank()) Text("Mirror account: ${result.maskedAccount}")
            Text("Approved preview age: ${result.previewAgeMillis / 1000}s")
            PreviewCount("Owned remote snapshots", result.ownedRemote)
            PreviewCount("Unchanged", result.unchanged)
            PreviewCount("New uploads", result.newUploaded)
            PreviewCount("Changed replacements", result.changedReplaced)
            PreviewCount("Previous snapshots moved to Trash", result.previousTrashed)
            PreviewCount("Remote-only moved to Trash", result.remoteOnlyTrashed)
            PreviewCount("Recoveries", result.recoveries)
            PreviewCount("Warnings", result.warnings)
            PreviewCount("Conflicts", result.conflicts)
            PreviewCount("Failed", result.failed)
            PreviewCount("Remaining", result.remaining)
            Text("Completion: ${result.state.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }}")
            Text("Resumable: ${if (result.resumable) "Yes" else "No"}")
            if (result.resumable) TextButton(onClick = onResume) { Text("Resume safe remaining actions") }
        }
    }
}
