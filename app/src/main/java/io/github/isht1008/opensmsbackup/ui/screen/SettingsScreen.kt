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
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator
import io.github.isht1008.opensmsbackup.ui.component.AccountProfileCard
import io.github.isht1008.opensmsbackup.ui.component.DisconnectAccountDialog
import io.github.isht1008.opensmsbackup.ui.component.PrimaryButton
import io.github.isht1008.opensmsbackup.ui.component.SettingCard
import io.github.isht1008.opensmsbackup.ui.component.TopBar
import io.github.isht1008.opensmsbackup.viewmodel.SettingsViewModel
import io.github.isht1008.opensmsbackup.viewmodel.SettingsViewModelFactory
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    val viewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(
                GmailAccountManager(context),
                GmailAccountCoordinator(context),
                GmailBackupWorkCoordinator(context),
                DeviceProfileStore.create(context)
            )
        )

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
            Text(
                text = "Device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            viewModel.deviceProfile?.let { device ->
                Spacer(Modifier.height(12.dp))
                Text("Model: ${device.manufacturer} ${device.model}")
                Text("Android ${device.androidVersion} • Device ${device.shortId}")
                Text("Primary: ${device.maskedPrimaryPhoneNumber ?: "Not set"}")
                device.secondaryPhoneNumber?.let {
                    Text("Secondary: ${io.github.isht1008.opensmsbackup.device.DeviceDisplayName.maskedNumber(it)}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.deviceNameDraft,
                    onValueChange = { viewModel.deviceNameDraft = it },
                    label = { Text("Device name") }
                )
                OutlinedTextField(
                    value = viewModel.primaryPhoneDraft,
                    onValueChange = { viewModel.primaryPhoneDraft = it },
                    label = { Text("Replace primary number (optional)") }
                )
                OutlinedTextField(
                    value = viewModel.secondaryPhoneDraft,
                    onValueChange = { viewModel.secondaryPhoneDraft = it },
                    label = { Text("Replace secondary number (optional)") }
                )
                OutlinedTextField(
                    value = viewModel.defaultRegionDraft,
                    onValueChange = { viewModel.defaultRegionDraft = it.uppercase(java.util.Locale.ROOT) },
                    label = { Text("Default country (ISO code)") }
                )
                Text("Used to interpret local phone numbers.")
                if (viewModel.normalizedNumberExample.isNotBlank()) {
                    Text("Example: 9876543210 → ${viewModel.normalizedNumberExample}")
                }
                viewModel.countryError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Text("Gmail: ${viewModel.deviceLabelPreview}")
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Save Device", viewModel::saveDeviceProfile)
                viewModel.deviceSaveMessage?.let { Text(it) }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Backup Gmail Accounts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            if (viewModel.accountProfiles.isEmpty()) {
                Text(
                    text = "No Gmail accounts connected.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                viewModel.accountProfiles.forEach { profile ->
                    AccountProfileCard(
                        profile = profile,
                        isSelected =
                            viewModel.selectedProfileId ==
                                    profile.profileId,
                        isBusy =
                            viewModel.isAddingAccount ||
                                    viewModel.busyProfileId != null,
                        onSelect = {
                            viewModel.selectAccount(profile)
                        },
                        onAuthorize = {
                            viewModel.reauthorizeAccount(profile)
                        },
                        onDisconnect = {
                            viewModel.requestDisconnect(profile)
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            PrimaryButton(
                text =
                    if (viewModel.isAddingAccount) {
                        "Adding account..."
                    } else {
                        "Add Account"
                    },
                onClick = viewModel::addAccount
            )

            viewModel.errorMessage?.let { error ->
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

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

    viewModel.pendingDisconnectProfile
        ?.let { profile ->
            DisconnectAccountDialog(
                profile = profile,
                isLastUsableAccount =
                    viewModel.isLastUsableDisconnect,
                onConfirm =
                    viewModel::confirmDisconnect,
                onDismiss =
                    viewModel::dismissDisconnect
            )
        }
}
