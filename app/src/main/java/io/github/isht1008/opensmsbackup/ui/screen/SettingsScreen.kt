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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.ui.component.GmailAccountDialog
import io.github.isht1008.opensmsbackup.ui.component.SettingCard
import io.github.isht1008.opensmsbackup.ui.component.TopBar
import io.github.isht1008.opensmsbackup.viewmodel.SettingsViewModel
import io.github.isht1008.opensmsbackup.viewmodel.SettingsViewModelFactory

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {}
) {

    val context = LocalContext.current

    val gmailAccountManager =
        GmailAccountManager(context)

    val gmailAccountCoordinator =
        GmailAccountCoordinator(context)

    val viewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(
                gmailAccountManager,
                gmailAccountCoordinator
            )
        )

    var showGmailDialog by remember {
        mutableStateOf(false)
    }

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
                .verticalScroll(
                    rememberScrollState()
                ),

            verticalArrangement = Arrangement.Top

        ) {

            SettingCard(

                title = "Backup Gmail Account",

                subtitle = when {

                    viewModel.isConnecting ->
                        "Connecting..."

                    viewModel.isConnected ->
                        "${viewModel.gmailAccount}\nTap to change account"

                    else ->
                        "Not connected\nTap to connect Gmail"

                },

                onClick = {

                    if (viewModel.isConnecting) {
                        return@SettingCard
                    }

                    if (viewModel.isConnected) {

                        showGmailDialog = true

                    } else {

                        viewModel.connectAccount()

                    }

                }

            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            SettingCard(
                title = "Backup Destination",
                subtitle = "JSON File",
                onClick = { }
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            SettingCard(
                title = "Include Contact Names",
                subtitle = "Enabled",
                onClick = { }
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            SettingCard(
                title = "Automatic Backup",
                subtitle = "Coming Soon",
                onClick = { }
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            SettingCard(
                title = "About",
                subtitle = "Version 0.2.0",
                onClick = { }
            )

        }

    }

    if (
        showGmailDialog &&
        viewModel.gmailAccount != null
    ) {

        GmailAccountDialog(

            email = viewModel.gmailAccount!!,

            onChangeAccount = {

                showGmailDialog = false
                viewModel.connectAccount()

            },

            onDisconnect = {

                showGmailDialog = false
                viewModel.removeAccount()

            },

            onDismiss = {

                showGmailDialog = false

            }

        )

    }

}