package io.github.isht1008.opensmsbackup.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity

@Composable
fun AccountProfileCard(
    profile: AccountProfileEntity,
    isSelected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onAuthorize: () -> Unit,
    onDisconnect: () -> Unit
) {
    val status =
        when (profile.connectionState) {
            AccountProfileEntity
                .CONNECTION_STATE_CONNECTED ->
                "Connected"

            AccountProfileEntity
                .CONNECTION_STATE_AUTHORIZATION_REQUIRED ->
                "Authorization Required"

            AccountProfileEntity
                .CONNECTION_STATE_DISCONNECTED ->
                "Disconnected"

            else -> profile.connectionState
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = profile.accountEmail,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text =
                    if (isSelected) {
                        "$status • Selected account"
                    } else {
                        status
                    },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (
                        profile.connectionState ==
                        AccountProfileEntity
                            .CONNECTION_STATE_AUTHORIZATION_REQUIRED
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (profile.connectionState) {
                    AccountProfileEntity
                        .CONNECTION_STATE_CONNECTED -> {
                        if (!isSelected) {
                            TextButton(
                                enabled = !isBusy,
                                onClick = onSelect
                            ) {
                                Text("Select")
                            }
                        }

                        TextButton(
                            enabled = !isBusy,
                            onClick = onDisconnect
                        ) {
                            Text("Disconnect")
                        }
                    }

                    AccountProfileEntity
                        .CONNECTION_STATE_AUTHORIZATION_REQUIRED,
                    AccountProfileEntity
                        .CONNECTION_STATE_DISCONNECTED -> {
                        TextButton(
                            enabled = !isBusy,
                            onClick = onAuthorize
                        ) {
                            Text("Re-authorize")
                        }
                    }
                }
            }
        }
    }
}
