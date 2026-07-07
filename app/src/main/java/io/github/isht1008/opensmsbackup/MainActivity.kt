package io.github.isht1008.opensmsbackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.isht1008.opensmsbackup.ui.screen.HomeScreen
import io.github.isht1008.opensmsbackup.ui.theme.OpenSMSBackupTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->

        if (isGranted) {
            println("SMS permission granted")
        } else {
            println("SMS permission denied")
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            OpenSMSBackupTheme {
                HomeScreen(
                    onBackupClick = {

                        if (
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {

                            println("Already have SMS permission")

                        } else {

                            requestSmsPermission.launch(
                                Manifest.permission.READ_SMS
                            )

                        }

                    }
                )
            }
        }
    }
}