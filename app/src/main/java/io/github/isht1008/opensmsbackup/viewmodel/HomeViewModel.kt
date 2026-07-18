package io.github.isht1008.opensmsbackup.viewmodel

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import io.github.isht1008.opensmsbackup.backup.BackupHistoryRepository
import io.github.isht1008.opensmsbackup.backup.BackupManager
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.auth.GoogleSignInManager
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    var status by mutableStateOf("Ready")
        private set

    var isBackingUp by mutableStateOf(false)
        private set

    var progress by mutableStateOf(0f)
        private set

    var isGmailBackingUp by mutableStateOf(false)
        private set

    var gmailBackupProgress by mutableStateOf(0f)
        private set

    var onGmailConsentRequired: ((Intent) -> Unit)? = null

    fun updateStatus(
        newStatus: String
    ) {
        status = newStatus
    }

    fun startBackup(
        context: Context,
        includeContactNames: Boolean
    ) {

        if (isBackingUp || isGmailBackingUp) {
            return
        }

        isBackingUp = true
        progress = 0f

        updateStatus("Preparing backup...")

        viewModelScope.launch {

            try {

                val result = withContext(Dispatchers.IO) {

                    BackupManager().createBackup(
                        context = context,
                        includeContactNames = includeContactNames,
                        onProgress = { current, total ->

                            val calculatedProgress =
                                if (total > 0) {
                                    current.toFloat() / total.toFloat()
                                } else {
                                    0f
                                }

                            progress = calculatedProgress

                            val percent =
                                (calculatedProgress * 100).toInt()

                            updateStatus(
                                """
```

Reading SMS...

${String.format("%,d", current)} of ${String.format("%,d", total)}

$percent%
""".trimIndent()
                            )
                        }
                    )
                }

                progress = 1f

                updateStatus(
                    """
```

✅ Backup completed

📨 Messages
${String.format("%,d", result.totalMessages)}

👥 Conversations
${String.format("%,d", result.totalConversations)}

📄 Backup File
${result.backupFileName}

📁 Location
Documents/OpenSMSBackup
""".trimIndent()
                )

            } catch (error: Exception) {

                updateStatus(
                    """
```

ERROR

${error.javaClass.simpleName}

${error.message ?: "Unknown error"}
""".trimIndent()
                )

            } finally {

                isBackingUp = false
            }
        }
    }

    fun loadBackupHistory(
        context: Context
    ) {

        viewModelScope.launch {

            updateStatus("Loading backups...")

            try {

                val backups = withContext(Dispatchers.IO) {
                    BackupHistoryRepository().getBackups(context)
                }

                if (backups.isEmpty()) {

                    updateStatus("No backups found.")

                } else {

                    val latest = backups.first()

                    updateStatus(
                        """
```

Found ${backups.size} backup(s)

Latest:
${latest.displayName}

Messages:
${latest.messageCount}

Conversations:
${latest.conversationCount}
""".trimIndent()
                    )
                }

            } catch (error: Exception) {

                updateStatus(
                    """
```

Failed to load backups

${error.javaClass.simpleName}

${error.message ?: "Unknown error"}
""".trimIndent()
                )
            }
        }
    }

    fun signInGoogle(
        context: Context
    ) {

        if (isGmailBackingUp) {
            return
        }

        viewModelScope.launch {

            updateStatus("Signing in with Google...")

            val result =
                GoogleSignInManager(context).signIn()

            result.onSuccess { email ->

                withContext(Dispatchers.IO) {
                    GmailAccountManager(context).saveAccount(email)
                }

                updateStatus(
                    """
```

Google account connected

$email
""".trimIndent()
                )
            }

            result.onFailure { error ->

                updateStatus(
                    """
```

Google Sign In Failed

${error.message ?: "Unknown error"}
""".trimIndent()
                )
            }
        }
    }

    fun testGmailApi(
        context: Context
    ) {

        if (isGmailBackingUp) {
            return
        }

        viewModelScope.launch {

            updateStatus("Connecting to Gmail...")

            try {

                val account =
                    GmailAccountManager(context).getAccount()

                if (account.isNullOrBlank()) {

                    updateStatus("No Gmail account connected.")
                    return@launch
                }

                val result =
                    GmailApiClient(context).listLabels(account)

                result.onSuccess { response ->

                    updateStatus(
                        """
```

Gmail API Success

Labels found: ${response.labels?.size ?: 0}
""".trimIndent()
                    )
                }

                result.onFailure { error ->

                    handleGmailError(
                        error = error,
                        failureTitle = "Gmail API Failed"
                    )
                }

            } catch (error: Exception) {

                handleGmailError(
                    error = error,
                    failureTitle = "Gmail API Failed"
                )
            }
        }
    }

    fun backupSmsToGmail(
        context: Context,
        includeContactNames: Boolean,
        maxNewMessages: Int? = 25
    ) {

        if (isGmailBackingUp || isBackingUp) {
            return
        }

        isGmailBackingUp = true
        gmailBackupProgress = 0f

        viewModelScope.launch {

            try {

                updateStatus("Preparing Gmail backup...")

                val accountEmail =
                    GmailAccountManager(context).getAccount()

                if (accountEmail.isNullOrBlank()) {

                    updateStatus(
                        """
```

No Gmail account connected.

Tap Sign in with Google first.
""".trimIndent()
                    )

                    return@launch
                }

                val result =
                    GmailBackupManager().backup(
                        context = context,
                        accountEmail = accountEmail,
                        includeContactNames = includeContactNames,
                        maxNewMessages = maxNewMessages,
                        onProgress = {
                                current,
                                total,
                                uploaded,
                                skipped,
                                failed ->

                            val calculatedProgress =
                                if (total > 0) {
                                    current.toFloat() / total.toFloat()
                                } else {
                                    0f
                                }

                            gmailBackupProgress =
                                calculatedProgress

                            val percent =
                                (calculatedProgress * 100).toInt()

                            updateStatus(
                                """
```

Gmail backup in progress

Checked
${String.format("%,d", current)} of ${String.format("%,d", total)}

Uploaded
${String.format("%,d", uploaded)}

Skipped
${String.format("%,d", skipped)}

Failed
${String.format("%,d", failed)}

$percent%
""".trimIndent()
                            )
                        }
                    )

                result.onSuccess { summary ->

                    gmailBackupProgress =
                        if (
                            summary.totalMessages > 0 &&
                            !summary.stoppedAtSafetyLimit
                        ) {
                            1f
                        } else if (summary.totalMessages > 0) {
                            summary.checkedMessages.toFloat() /
                                    summary.totalMessages.toFloat()
                        } else {
                            0f
                        }

                    val safetyMessage =
                        if (summary.stoppedAtSafetyLimit) {
                            """
```

Safety test limit reached.

Only ${String.format("%,d", summary.uploadedMessages)} new messages were uploaded.
""".trimIndent()
                        } else {
                            ""
                        }

                    val failureDetails =
                        if (summary.failures.isNotEmpty()) {
                            """
```

First errors:

${summary.failures.joinToString("\n")}
""".trimIndent()
                        } else {
                            ""
                        }

                    updateStatus(
                        """
```

Gmail backup completed

Total SMS on device
${String.format("%,d", summary.totalMessages)}

Checked
${String.format("%,d", summary.checkedMessages)}

Uploaded
${String.format("%,d", summary.uploadedMessages)}

Skipped
${String.format("%,d", summary.skippedMessages)}

Failed
${String.format("%,d", summary.failedMessages)}
$safetyMessage
$failureDetails
""".trimIndent()
                    )
                }

                result.onFailure { error ->

                    handleGmailError(
                        error = error,
                        failureTitle = "Gmail backup failed"
                    )
                }

            } catch (error: Exception) {

                handleGmailError(
                    error = error,
                    failureTitle = "Gmail backup failed"
                )

            } finally {

                isGmailBackingUp = false
            }
        }
    }

    private fun handleGmailError(
        error: Throwable,
        failureTitle: String
    ) {

        val recoverableError =
            findRecoverableAuthError(error)

        if (recoverableError != null) {

            updateStatus(
                "Opening Gmail permission screen..."
            )

            onGmailConsentRequired?.invoke(
                recoverableError.intent
            )

            return
        }

        updateStatus(
            """
```

$failureTitle

${error.javaClass.name}

Message:
${error.message ?: "Unknown error"}
""".trimIndent()
        )
    }

    private fun findRecoverableAuthError(
        error: Throwable
    ): UserRecoverableAuthIOException? {

        var currentError: Throwable? = error

        while (currentError != null) {

            if (
                currentError is
                        UserRecoverableAuthIOException
            ) {
                return currentError
            }

            currentError = currentError.cause
        }

        return null
    }

}
