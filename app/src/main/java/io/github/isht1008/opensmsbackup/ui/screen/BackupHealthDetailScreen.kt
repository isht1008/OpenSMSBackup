package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.health.BackupHealthCalculator
import io.github.isht1008.opensmsbackup.viewmodel.BackupHealthDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BackupHealthDetailScreen(viewModel: BackupHealthDetailViewModel, onBackClick: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Health Check Details") }, navigationIcon = {
        TextButton(onClick = onBackClick) { Text("Back") }
    }) }) { padding ->
        val item = viewModel.item
        if (viewModel.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
        else if (item == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("This verification entry is no longer available.")
        } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Detail("Status", item.status.replace('_', ' '))
            Detail("Account", item.accountEmail); Detail("Device", item.deviceName)
            Detail("Mode", item.mode.replace('_', ' ')); Detail("Messages", "${item.localMessageCount}")
            Detail("Conversations", "${item.localConversationCount}"); Detail("Matched", "${item.matchedMessageCount}")
            Detail("Missing", "${item.missingMessageCount}"); Detail("Extra archived", "${item.unexpectedArchivedMessageCount}")
            Detail("Duplicates", "${item.duplicateFingerprintCount}"); Detail("Unreadable", "${item.unreadableArchiveCount}")
            Detail("Integrity", "${item.verificationPercent.toInt()}% represented")
            Detail("Started", BackupHealthCalculator.formatDate(item.startedAt))
            Detail("Completed", BackupHealthCalculator.formatDate(item.completedAt))
            Detail("Duration", BackupHealthCalculator.formatDuration(item.completedAt - item.startedAt))
            Detail("Summary", BackupHealthCalculator.summary(item))
            Text("Issue-level details are summarized by missing, extra, duplicate, and unreadable counts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Detail(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(value) }
}
