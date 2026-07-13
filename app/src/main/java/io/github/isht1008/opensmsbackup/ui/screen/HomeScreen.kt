package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isht1008.opensmsbackup.ui.component.PrimaryButton
import io.github.isht1008.opensmsbackup.ui.component.StatusCard
import io.github.isht1008.opensmsbackup.ui.theme.OpenSMSBackupTheme
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBackupClick: () -> Unit,
    onBackupHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGoogleSignInClick: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "OpenSMS Backup",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Privacy-first SMS backup\nfor your Gmail account",
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            PrimaryButton(
                text = if (viewModel.isBackingUp)
                    "Backing up..."
                else
                    "Backup SMS",
                onClick = {
                    if (!viewModel.isBackingUp) {
                        viewModel.updateStatus("Checking permissions...")
                        onBackupClick()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Backup History",
                onClick = onBackupHistoryClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Sign in with Google",
                onClick = onGoogleSignInClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Restore SMS",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Settings",
                onClick = onSettingsClick
            )

            if (viewModel.isBackingUp) {

                Spacer(modifier = Modifier.height(32.dp))

                LinearProgressIndicator()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please wait..."
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            StatusCard(
                status = viewModel.status,
                progress = if (viewModel.isBackingUp)
                    viewModel.progress
                else
                    null
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Version 0.2.0"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {

    OpenSMSBackupTheme {

        HomeScreen(
            viewModel = HomeViewModel(),
            onBackupClick = {},
            onBackupHistoryClick = {},
            onSettingsClick = {},
            onGoogleSignInClick = {}
        )

    }

}