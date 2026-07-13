package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.ui.component.SettingCard
import io.github.isht1008.opensmsbackup.ui.component.TopBar
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isht1008.opensmsbackup.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {

    Scaffold(

        topBar = {
            TopBar(
                title = "Settings",
                onBackClick = onBackClick
            )
        }

    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {

            SettingCard(
                title = "Gmail Backup",
                subtitle = if (viewModel.isConnected) {
                    viewModel.gmailAccount ?: ""
                } else {
                    "Not connected"
                },
                onClick = { }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingCard(
                title = "Backup Destination",
                subtitle = "JSON File",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingCard(
                title = "Include Contact Names",
                subtitle = "Enabled",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingCard(
                title = "Automatic Backup",
                subtitle = "Coming Soon",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingCard(
                title = "About",
                subtitle = "Version 0.2.0",
                onClick = { }
            )

        }

    }

}