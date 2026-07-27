package io.github.isht1008.opensmsbackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.github.isht1008.opensmsbackup.navigation.OpenSmsBackupNavHost
import io.github.isht1008.opensmsbackup.ui.theme.OpenSMSBackupTheme
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    private val gmailConsentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {
                homeViewModel.updateStatus(
                    "Gmail permission granted. Tap Gmail backup again."
                )
            } else {
                homeViewModel.updateStatus(
                    "Gmail permission denied."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        homeViewModel.onGmailConsentRequired = { intent ->
            gmailConsentLauncher.launch(intent)
        }

        setContent {
            OpenSMSBackupTheme {
                OpenSmsBackupNavHost(
                    homeViewModel = homeViewModel,
                    onBackupClick = { includeContactNames ->
                        homeViewModel.startBackup(
                            context = this@MainActivity,
                            includeContactNames = includeContactNames
                        )
                    }
                )
            }
        }
    }
}
