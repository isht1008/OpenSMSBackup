package io.github.isht1008.opensmsbackup.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.isht1008.opensmsbackup.ui.theme.OpenSMSBackupTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.ui.component.PrimaryButton

@Composable
fun HomeScreen() {

    var status by remember {
        mutableStateOf("Ready")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                text = "Backup SMS",
                onClick = {
                    status = "Backup button pressed"
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Restore SMS",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Settings",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Status",
                fontWeight = FontWeight.Bold
            )

            Text(
                text = status
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Version 0.1.0"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    OpenSMSBackupTheme {
        HomeScreen()
    }
}