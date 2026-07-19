package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import io.github.isht1008.opensmsbackup.health.BackupHealthCalculator
import io.github.isht1008.opensmsbackup.health.BackupHealthFilter
import io.github.isht1008.opensmsbackup.ui.component.PrimaryButton
import io.github.isht1008.opensmsbackup.viewmodel.BackupHealthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BackupHealthScreen(
    viewModel: BackupHealthViewModel,
    onBackClick: () -> Unit,
    onCreateBackup: () -> Unit,
    onOpenDetails: (Long) -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Backup Health") }, navigationIcon = {
        TextButton(onClick = onBackClick) { Text("Back") }
    }) }) { padding ->
        when {
            viewModel.isLoading -> LoadingHealth(Modifier.padding(padding))
            viewModel.history.isEmpty() && viewModel.localBackups.isEmpty() -> EmptyHealth(Modifier.padding(padding), onCreateBackup)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { LatestBackupCard(viewModel) }
                item { LatestVerificationCard(viewModel.latestVerification, viewModel.isVerificationRunning) }
                item { ArchiveHealthCard(viewModel) }
                item { StatisticsCard(viewModel) }
                item { Text("Health trend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(viewModel.history.take(8), key = { "trend-${it.id}" }) { TrendRow(it) }
                item { Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item {
                    OutlinedTextField(
                        value = viewModel.search,
                        onValueChange = { viewModel.search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search account, device, or date") },
                        singleLine = true
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackupHealthFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = viewModel.filter == filter,
                                onClick = { viewModel.filter = filter },
                                label = { Text(filter.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) }
                            )
                        }
                    }
                }
                if (viewModel.filteredHistory.isEmpty()) item {
                    Text("No history matches this filter.", modifier = Modifier.padding(vertical = 24.dp))
                }
                items(viewModel.filteredHistory, key = { it.id }) { item ->
                    HistoryCard(item, onOpenDetails)
                }
            }
        }
    }
}

@Composable private fun LatestBackupCard(viewModel: BackupHealthViewModel) {
    val local = viewModel.latestLocalBackup
    HealthSection("Latest Backup") {
        if (local == null) Text("No local JSON backup is available.") else {
            KeyValue("Status", "Completed")
            KeyValue("Started", "Not recorded for local backups")
            KeyValue("Finished", local.createdAt.ifBlank { "Unknown" })
            KeyValue("Backup mode", "Local JSON")
            KeyValue("Account", "Local backup — no Gmail account")
            KeyValue("Device", "Current installation")
            KeyValue("Messages", "${local.messageCount}")
            KeyValue("Conversations", "${local.conversationCount}")
            KeyValue("Duration", "Not recorded for local backups")
        }
    }
}

@Composable private fun LatestVerificationCard(item: BackupVerificationEntity?, running: Boolean) {
    HealthSection("Latest Verification") {
        if (running) StatusBadge("RUNNING")
        if (item == null) Text("No Gmail verification has completed yet.") else {
            if (!running) StatusBadge(item.status)
            KeyValue("Integrity", "${item.verificationPercent.toInt()}% represented")
            KeyValue("Matched", "${item.matchedMessageCount}")
            KeyValue("Missing", "${item.missingMessageCount}")
            KeyValue("Duplicates", "${item.duplicateFingerprintCount}")
            KeyValue("Unreadable", "${item.unreadableArchiveCount}")
            KeyValue("Completed", BackupHealthCalculator.formatDate(item.completedAt))
        }
    }
}

@Composable private fun ArchiveHealthCard(viewModel: BackupHealthViewModel) {
    val health = viewModel.archiveHealth
    val color = when (health.level) {
        "GREEN" -> Color(0xFF1B5E20)
        "YELLOW" -> Color(0xFFF9A825)
        "RED" -> Color(0xFFB71C1C)
        else -> MaterialTheme.colorScheme.outline
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()
        .semantics { contentDescription = "Archive health: ${health.title}. ${health.explanation}" }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(health.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(health.explanation)
        }
    }
}

@Composable private fun StatisticsCard(viewModel: BackupHealthViewModel) {
    val s = viewModel.statistics
    val largest = maxOf(s.largestBackupMessages, viewModel.localBackups.maxOfOrNull { it.messageCount } ?: 0)
    HealthSection("Statistics") {
        KeyValue("Total backups", "${viewModel.localBackups.size}")
        KeyValue("Successful backups", "${viewModel.localBackups.count { it.createdAt.isNotBlank() }}")
        KeyValue("Verified backups", "${s.verifiedRuns}")
        KeyValue("Health checks", "${s.totalRuns}")
        KeyValue("Average integrity", "${s.averageIntegrity.toInt()}% represented")
        KeyValue("Average verification duration", BackupHealthCalculator.formatDuration(s.averageDurationMillis))
        KeyValue("Largest backup scope", "$largest messages")
        s.mostRecentAccount?.let { KeyValue("Most recent account", it) }
        s.mostRecentDevice?.let { KeyValue("Most recent device", it) }
    }
}

@Composable private fun TrendRow(item: BackupVerificationEntity) {
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = "${BackupHealthCalculator.formatDate(item.completedAt)}, ${item.verificationPercent.toInt()} percent represented, ${item.status}"
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(BackupHealthCalculator.formatDate(item.completedAt), style = MaterialTheme.typography.bodySmall)
            Text(item.status.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { (item.verificationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
    }
}

@Composable private fun HistoryCard(item: BackupVerificationEntity, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(item.id) }.semantics {
        contentDescription = "Open details for ${item.status} verification completed ${BackupHealthCalculator.formatDate(item.completedAt)}"
    }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(BackupHealthCalculator.formatDate(item.completedAt), fontWeight = FontWeight.SemiBold)
            StatusBadge(item.status)
            Text(item.mode.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
            Text("${item.localMessageCount} messages · ${BackupHealthCalculator.formatDuration(item.completedAt - item.startedAt)}")
            Text(item.accountEmail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun StatusBadge(status: String) {
    val color = when (status) {
        "VERIFIED" -> Color(0xFF2E7D32)
        "PARTIALLY_VERIFIED" -> Color(0xFFF9A825)
        "FAILED" -> Color(0xFFC62828)
        "RUNNING" -> Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.outline
    }
    val contentColor = if (status == "PARTIALLY_VERIFIED") Color.Black else Color.White
    Text(status.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable private fun HealthSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); content()
    } }
}

@Composable private fun KeyValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun LoadingHealth(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))) }
    }
}

@Composable private fun EmptyHealth(modifier: Modifier, onCreateBackup: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No backups yet.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp)); Text("Create a backup, then verify its Gmail archive health.")
        Spacer(Modifier.height(24.dp)); PrimaryButton("Create first backup", onCreateBackup)
    }
}
