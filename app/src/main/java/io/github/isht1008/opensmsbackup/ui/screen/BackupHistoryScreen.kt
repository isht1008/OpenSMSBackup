package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.ui.component.BackupHistoryCard
import io.github.isht1008.opensmsbackup.viewmodel.BackupHistoryViewModel

@Composable
fun BackupHistoryScreen(
    viewModel: BackupHistoryViewModel,
    onBackClick: () -> Unit
) {

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadBackups(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        if (viewModel.isLoading) {

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }

        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                items(viewModel.backups) { backup ->

                    BackupHistoryCard(
                        backup = backup,
                        onClick = {

                            viewModel.selectBackup(backup)

                        }
                    )

                }

            }

        }

    }

}