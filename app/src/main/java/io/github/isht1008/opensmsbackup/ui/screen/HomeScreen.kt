package io.github.isht1008.opensmsbackup.ui.screen

import android.Manifest
import android.os.Build
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
import io.github.isht1008.opensmsbackup.ui.component.GmailAccountManagementCard
import io.github.isht1008.opensmsbackup.viewmodel.HomeViewModel
import java.text.DateFormat
import java.util.Date

private enum class PendingBackupAction {
    LOCAL,
    GMAIL,
    VERIFY
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
                viewModel.isGmailBackingUp || viewModel.isVerifying

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
                    notificationsEnabled =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                )
            }

            PendingBackupAction.VERIFY -> viewModel.startVerification()

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

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                permissions[Manifest.permission.POST_NOTIFICATIONS] == false
            ) {
                viewModel.updateStatus(
                    "Notification permission was denied. Android may hide Gmail backup progress from the notification drawer, but the foreground backup can continue."
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

        val notificationPermissionNeeded =
            action != PendingBackupAction.LOCAL &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED

        if (hasSmsPermission() && !notificationPermissionNeeded) {
            startPendingBackup()
            return
        }

        viewModel.updateStatus(
            "Requesting SMS permission..."
        )

        val permissions = mutableListOf(Manifest.permission.READ_SMS)
        if (action != PendingBackupAction.VERIFY) permissions += Manifest.permission.READ_CONTACTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
            viewModel.updateStatus(
                "Notifications show background Gmail backup progress and provide a Cancel action."
            )
        }
        permissionLauncher.launch(permissions.toTypedArray())
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
                },
                enabled = !backupInProgress
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (viewModel.isGmailBackingUp) {
                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                PrimaryButton(
                    text =
                        if (
                            viewModel
                                .isGmailBackupCancellationRequested
                        ) {
                            "Cancelling Gmail Backup…"
                        } else {
                            "Cancel Gmail Backup"
                    },
                    onClick = viewModel::cancelGmailBackup,
                    enabled = viewModel.gmailBackupUiState.isCancellable
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            GmailAccountManagementCard(
                profile = viewModel.selectedGmailProfile,
                policyState = viewModel.gmailBackupModeUiState,
                lastBackupTime = viewModel.lastGmailBackupTime,
                verification = viewModel.latestVerification,
                gmailBackupActive = viewModel.isGmailBackingUp,
                allBackupActionsBusy = backupInProgress,
                onBackup = {
                    requestPermissionsAndRun(
                        PendingBackupAction.GMAIL
                    )
                },
                onHistory = onBackupHistoryClick
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            PrimaryButton(
                text = "Settings",
                onClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = if (viewModel.isVerifying) "Verifying backup…" else "Verify backup",
                onClick = { requestPermissionsAndRun(PendingBackupAction.VERIFY) },
                enabled = !backupInProgress
            )

            if (viewModel.isVerifying) {
                Spacer(modifier = Modifier.height(8.dp))
                viewModel.verificationProgress?.let {
                    Text("${it.stage.name.replace('_', ' ')} · ${it.processed}${it.total?.let { total -> " / $total" }.orEmpty()}")
                }
                PrimaryButton("Cancel verification", viewModel::cancelVerification)
            }

            viewModel.latestVerification?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Latest verification", fontWeight = FontWeight.Bold)
                Text("${result.status.replace('_', ' ')} · ${result.verificationPercent.toInt()}% represented")
                Text("Account: ${result.accountEmail}\nDevice: ${result.deviceName}\nMode: ${result.mode}")
                Text("Local ${result.localMessageCount} · Archived ${result.archivedMessageCount} · Matched ${result.matchedMessageCount}")
                Text("Missing ${result.missingMessageCount} · Extra ${result.unexpectedArchivedMessageCount} · Duplicates ${result.duplicateFingerprintCount} · Unreadable ${result.unreadableArchiveCount}")
                Text(result.shortSummary)
                Text("Completed: ${DateFormat.getDateTimeInstance().format(Date(result.completedAt))}")
            }

            if (backupInProgress) {
                Spacer(
                    modifier = Modifier.height(32.dp)
                )

                val gmailState = viewModel.gmailBackupUiState
                val verificationState = viewModel.verificationProgress
                val verificationFraction = verificationState?.total
                    ?.takeIf { it > 0 }
                    ?.let { verificationState.processed.toFloat() / it.toFloat() }
                if (viewModel.isGmailBackingUp && gmailState.fraction != null) {
                    LinearProgressIndicator(
                        progress = { gmailState.fraction }
                    )
                } else if (viewModel.isVerifying && verificationFraction != null) {
                    LinearProgressIndicator(
                        progress = { verificationFraction.coerceIn(0f, 1f) }
                    )
                } else {
                    LinearProgressIndicator()
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        when {
                            viewModel.isGmailBackingUp -> buildString {
                                append("Gmail Backup")
                                gmailState.accountEmail?.let {
                                    append("\nAccount: $it")
                                }
                                if (gmailState.total > 0) {
                                    append("\nChecked: ${gmailState.checked} / ${gmailState.total}")
                                    append("\nUploaded: ${gmailState.uploaded}")
                                    append("\nUnchanged: ${gmailState.unchanged}")
                                    append("\nFailed: ${gmailState.failed}")
                                }
                                append("\nStatus: ${gmailState.phase}")
                            }
                            viewModel.isVerifying -> buildString {
                                append("Verifying Gmail backup...")
                                verificationState?.let { progress ->
                                    append("\nStage: ${progress.stage.name.replace('_', ' ')}")
                                    append("\nProcessed: ${progress.processed}")
                                    progress.total?.let { total ->
                                        append(" / $total conversations/messages")
                                    }
                                }
                            }
                            else -> "Creating local backup..."
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
