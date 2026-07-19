package io.github.isht1008.opensmsbackup.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isht1008.opensmsbackup.ui.component.PrimaryButton
import io.github.isht1008.opensmsbackup.viewmodel.RestoreViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RestoreScreen(onBackClick: () -> Unit, viewModel: RestoreViewModel = viewModel()) {
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onRoleResult(it.resultCode == Activity.RESULT_OK)
    }
    var showRoleExplanation by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Restore SMS") }, navigationIcon = {
        TextButton(onClick = onBackClick) { Text("Back") }
    }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(viewModel.status) }
            item { Text("Gmail account", style = MaterialTheme.typography.titleMedium) }
            items(viewModel.accounts) { account ->
                FilterChip(selected = viewModel.selectedAccount?.profileId == account.profileId,
                    onClick = { viewModel.chooseAccount(account) }, label = { Text(account.accountEmail) })
            }
            if (viewModel.devices.isNotEmpty()) item { Text("Source device", style = MaterialTheme.typography.titleMedium) }
            items(viewModel.devices) { device ->
                FilterChip(selected = viewModel.selectedDevice?.deviceId == device.deviceId,
                    onClick = { viewModel.chooseDevice(device) }, label = { Text(device.displayName) })
            }
            viewModel.preview?.let { preview ->
                item {
                    Text("${preview.conversations.size} conversations · ${preview.messageCount} messages")
                    Text("Received ${preview.receivedCount} · Sent ${preview.sentCount} · Estimated duplicates ${preview.estimatedDuplicates}")
                    val range = listOfNotNull(preview.earliestDate, preview.latestDate)
                        .joinToString(" – ") { DateFormat.getDateTimeInstance().format(Date(it)) }
                    if (range.isNotBlank()) Text(range)
                    OutlinedTextField(value = viewModel.search, onValueChange = { viewModel.search = it },
                        label = { Text("Search contact, number, or sender ID") })
                    Row { TextButton(onClick = { viewModel.selectAll(true) }) { Text("Select all") }
                        TextButton(onClick = { viewModel.selectAll(false) }) { Text("Clear") } }
                }
                items(viewModel.filteredConversations, key = { it.key }) { conversation ->
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(conversation.key in viewModel.selectedKeys, { viewModel.toggle(conversation.key) })
                        Column { Text(conversation.snapshot.contactName ?: conversation.snapshot.address ?: "Unknown sender")
                            Text("${conversation.snapshot.messageCount} messages") }
                    }
                }
                item { PrimaryButton("Restore selected messages", { showRoleExplanation = true }, !viewModel.loading) }
            }
            viewModel.result?.let { result -> item {
                Text("Restore result", style = MaterialTheme.typography.titleMedium)
                val sourceAccount = viewModel.resultAccount ?: viewModel.preview?.accountEmail
                val sourceDevice = viewModel.resultDevice ?: viewModel.preview?.sourceDevice?.displayName
                if (sourceAccount != null && sourceDevice != null) Text("$sourceAccount · $sourceDevice")
                Text("Restored ${result.restored} · Skipped ${result.skippedDuplicates} · Failed ${result.failed}")
                if (result.cancelled) Text("Cancelled after partial completion")
                Text("Set your preferred messaging app back as default when finished.")
                viewModel.workId?.let { PrimaryButton("Cancel restore", viewModel::cancel) }
            } }
        }
    }
    if (showRoleExplanation) AlertDialog(onDismissRequest = { showRoleExplanation = false },
        title = { Text("Temporary default SMS access required") },
        text = { Text("Android only permits the default SMS app to insert messages. OpenSMSBackup will request this role and will never switch it silently. After restore, return your preferred messaging app to default.") },
        confirmButton = { TextButton(onClick = { showRoleExplanation = false; viewModel.beginRestore(roleLauncher::launch) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showRoleExplanation = false }) { Text("Cancel") } })
}
