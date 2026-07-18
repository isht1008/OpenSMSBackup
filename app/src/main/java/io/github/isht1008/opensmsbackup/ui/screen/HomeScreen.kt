package io.github.isht1008.opensmsbackup.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.isht1008.opensmsbackup.ui.component.PrimaryButton
import io.github.isht1008.opensmsbackup.ui.component.StatusCard
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel

private enum class PendingBackupAction {
    LOCAL,
    GMAIL
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBackupClick: () -> Unit,
    onBackupHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshAccountStatus(
            context
        )
    }

    val backupInProgress =
        viewModel.isBackingUp ||
                viewModel.isGmailBackingUp

    var pendingBackupAction by remember {
        mutableStateOf<PendingBackupAction?>(null)
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startPendingBackup() {
        when (pendingBackupAction) {
            PendingBackupAction.LOCAL -> {
                onBackupClick()
            }

            PendingBackupAction.GMAIL -> {
                viewModel.backupSmsToGmail(
                    context = context,
                    includeContactNames = hasContactsPermission(),
                    maxConversations = 3
                )
            }

            null -> Unit
        }

        pendingBackupAction = null
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val smsGranted =
                permissions[
                    Manifest.permission.READ_SMS
                ] ?: hasSmsPermission()

            if (!smsGranted) {
                pendingBackupAction = null

                viewModel.updateStatus(
                    "SMS permission is required to back up messages."
                )

                return@rememberLauncherForActivityResult
            }

            val contactsGranted =
                permissions[
                    Manifest.permission.READ_CONTACTS
                ] ?: hasContactsPermission()

            if (!contactsGranted) {
                viewModel.updateStatus(
                    "Contacts permission was denied. Backup will continue without contact names."
                )
            }

            startPendingBackup()
        }

    fun requestPermissionsAndRun(
        action: PendingBackupAction
    ) {
        if (backupInProgress) {
            return
        }

        pendingBackupAction = action

        if (hasSmsPermission()) {
            startPendingBackup()
            return
        }

        viewModel.updateStatus(
            "Requesting SMS permission..."
        )

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(24.dp),
            verticalArrangement =
                Arrangement.Center,
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = "OpenSMS Backup",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "Privacy-first SMS backup\n" +
                            "for your Gmail account",
                fontSize = 18.sp
            )

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            PrimaryButton(
                text =
                    if (viewModel.isBackingUp) {
                        "Backing up..."
                    } else {
                        "Backup SMS"
                    },
                onClick = {
                    requestPermissionsAndRun(
                        PendingBackupAction.LOCAL
                    )
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            PrimaryButton(
                text = "Backup History",
                onClick = {
                    if (!backupInProgress) {
                        onBackupHistoryClick()
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            PrimaryButton(
                text = "Test Gmail API",
                onClick = {
                    if (!backupInProgress) {
                        viewModel.testGmailApi(
                            context
                        )
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            PrimaryButton(
                text =
                    if (viewModel.isGmailBackingUp) {
                        "Backing up to Gmail..."
                    } else {
                        "Test Gmail Backup - 3 Conversations"
                    },
                onClick = {
                    requestPermissionsAndRun(
                        PendingBackupAction.GMAIL
                    )
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            PrimaryButton(
                text = "Restore SMS",
                onClick = {
                    if (!backupInProgress) {
                        viewModel.updateStatus(
                            "Restore is not available yet."
                        )
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            PrimaryButton(
                text = "Settings",
                onClick = {
                    if (!backupInProgress) {
                        onSettingsClick()
                    }
                }
            )

            if (backupInProgress) {
                Spacer(
                    modifier = Modifier.height(32.dp)
                )

                LinearProgressIndicator()

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        if (viewModel.isGmailBackingUp) {
                            "Uploading conversations to Gmail..."
                        } else {
                            "Creating local backup..."
                        }
                )
            }

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            StatusCard(
                status =
                    if (viewModel.status == "Ready") {
                        viewModel.accountStatus
                    } else {
                        "${viewModel.accountStatus}\n\n${viewModel.status}"
                    },
                progress =
                    when {
                        viewModel.isBackingUp ->
                            viewModel.progress

                        viewModel.isGmailBackingUp ->
                            viewModel.gmailBackupProgress

                        else ->
                            null
                    }
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "Version 0.3.0"
            )
        }
    }
}
