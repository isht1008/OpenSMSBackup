package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.model.BackupHistoryItem
import io.github.isht1008.opensmsbackup.util.DateUtils
import io.github.isht1008.opensmsbackup.util.FileSizeUtils

@Composable
fun BackupHistoryCard(
    backup: BackupHistoryItem,
    onClick: () -> Unit = {}
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = backup.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "📅 ${DateUtils.formatIsoDate(backup.createdAt)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "💬 ${String.format("%,d", backup.messageCount)} SMS",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "👥 ${String.format("%,d", backup.conversationCount)} Conversations",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "💾 ${FileSizeUtils.format(backup.fileSizeBytes)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}