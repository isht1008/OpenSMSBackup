package io.github.isht1008.opensmsbackup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import io.github.isht1008.opensmsbackup.ui.theme.OpenSMSBackupTheme
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel
import io.github.isht1008.opensmsbackup.navigation.OpenSmsBackupNavHost
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        if (!granted) {
            homeViewModel.updateStatus(
                "SMS permission is required to create a backup."
            )
            return@registerForActivityResult
        }

        val contactsGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

        if (contactsGranted) {

            homeViewModel.updateStatus("Reading SMS...")

            homeViewModel.startBackup(
                context = this,
                includeContactNames = true
            )

        } else {

            requestContactsPermission.launch(
                Manifest.permission.READ_CONTACTS
            )

        }
    }

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        if (granted) {
            homeViewModel.updateStatus("Reading SMS...")
        } else {
            homeViewModel.updateStatus(
                "Reading SMS...\n(Contact names unavailable)"
            )
        }

        homeViewModel.startBackup(
            context = this,
            includeContactNames = granted
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            OpenSMSBackupTheme {

                OpenSmsBackupNavHost(
                    homeViewModel = homeViewModel,
                    onBackupClick = {

                        val smsGranted =
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED

                        val contactsGranted =
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED

                        if (!smsGranted) {

                            requestSmsPermission.launch(
                                Manifest.permission.READ_SMS
                            )

                        } else if (!contactsGranted) {

                            requestContactsPermission.launch(
                                Manifest.permission.READ_CONTACTS
                            )

                        } else {

                            homeViewModel.updateStatus("Reading SMS...")

                            homeViewModel.startBackup(
                                context = this,
                                includeContactNames = true
                            )

                        }
                    }
                )
            }
        }
    }
}