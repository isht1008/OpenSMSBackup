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
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupManager
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    var status by mutableStateOf("Ready")
        private set

    var accountStatus by
        mutableStateOf(
            "No Gmail account configured."
        )
        private set

    var isBackingUp by mutableStateOf(false)
        private set

    var progress by mutableStateOf(0f)
        private set

    var isGmailBackingUp by mutableStateOf(false)
        private set

    var isGmailBackupCancellationRequested by
        mutableStateOf(false)
        private set

    private var activeGmailBackupJob: Job? = null

    var gmailBackupProgress by mutableStateOf(0f)
        private set

    var onGmailConsentRequired: ((Intent) -> Unit)? = null

    fun updateStatus(
        newStatus: String
    ) {
        status = newStatus
    }

    fun refreshAccountStatus(
        context: Context
    ) {
        viewModelScope.launch {
            val profile =
                GmailAccountManager(context)
                    .getSelectedAccountProfile()

            updateAccountStatus(profile)
        }
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
                val accountProfile =
                    resolveSelectedProfile(
                        context
                    ) ?: return@launch

                updateAccountStatus(
                    accountProfile
                )

                val result =
                    withContext(Dispatchers.IO) {
                        BackupManager().createBackup(
                            context = context,
                            includeContactNames =
                                includeContactNames,
                            onProgress = {
                                    current,
                                    total ->

                                val calculatedProgress =
                                    if (total > 0) {
                                        current.toFloat() /
                                                total.toFloat()
                                    } else {
                                        0f
                                    }

                                progress =
                                    calculatedProgress

                                val percent =
                                    (calculatedProgress * 100)
                                        .toInt()

                                updateStatus(
                                    """
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
                    Backup completed

                    Messages
                    ${String.format("%,d", result.totalMessages)}

                    Conversations
                    ${String.format("%,d", result.totalConversations)}

                    Backup file
                    ${result.backupFileName}

                    Location
                    Documents/OpenSMSBackup
                    """.trimIndent()
                )

            } catch (error: Exception) {
                updateStatus(
                    """
                    Backup failed

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
                val backups =
                    withContext(Dispatchers.IO) {
                        BackupHistoryRepository()
                            .getBackups(context)
                    }

                if (backups.isEmpty()) {
                    updateStatus("No backups found.")
                } else {
                    val latest = backups.first()

                    updateStatus(
                        """
                        Found ${backups.size} backup(s)

                        Latest
                        ${latest.displayName}

                        Messages
                        ${latest.messageCount}

                        Conversations
                        ${latest.conversationCount}
                        """.trimIndent()
                    )
                }

            } catch (error: Exception) {
                updateStatus(
                    """
                    Failed to load backups

                    ${error.javaClass.simpleName}

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
                val accountProfile =
                    resolveSelectedProfile(
                        context
                    ) ?: return@launch

                val result =
                    GmailApiClient(context)
                        .listLabels(
                            accountProfile
                        )

                result.onSuccess { response ->
                    updateStatus(
                        """
                        Gmail API success

                        Labels found: ${response.labels?.size ?: 0}
                        """.trimIndent()
                    )
                }

                result.onFailure { error ->
                    handleGmailError(
                        error = error,
                        failureTitle =
                            "Gmail API failed"
                    )
                }

            } catch (error: Exception) {
                handleGmailError(
                    error = error,
                    failureTitle =
                        "Gmail API failed"
                )
            }
        }
    }

    fun backupSmsToGmail(
        context: Context,
        includeContactNames: Boolean,
        maxConversations: Int? = 3
    ) {

        if (
            activeGmailBackupJob?.isActive == true ||
            isGmailBackingUp ||
            isBackingUp
        ) {
            return
        }

        isGmailBackingUp = true
        gmailBackupProgress = 0f

        isGmailBackupCancellationRequested = false

        var completed = 0
        var lastTotal = 0
        var lastUploaded = 0
        var lastSkipped = 0
        var lastFailed = 0
        var activeProfileId: String? = null

        activeGmailBackupJob = viewModelScope.launch {

            try {
                updateStatus(
                    "Preparing conversation backup..."
                )

                val accountProfile =
                    resolveSelectedProfile(
                        context
                    ) ?: return@launch

                activeProfileId = accountProfile.profileId

                check(
                    GmailBackupSession.begin(
                        accountProfile.profileId
                    )
                ) {
                    "A Gmail backup is already running."
                }

                val result =
                    GmailBackupManager().backup(
                        context = context,
                        accountProfile =
                            accountProfile,
                        includeContactNames =
                            includeContactNames,
                        maxConversations =
                            maxConversations,
                        onProgress = {
                                current,
                                total,
                                uploaded,
                                skipped,
                                failed ->

                            completed = current
                            lastTotal = total
                            lastUploaded = uploaded
                            lastSkipped = skipped
                            lastFailed = failed

                            val calculatedProgress =
                                if (total > 0) {
                                    current.toFloat() /
                                            total.toFloat()
                                } else {
                                    0f
                                }

                            gmailBackupProgress =
                                calculatedProgress

                            val percent =
                                (calculatedProgress * 100)
                                    .toInt()

                            updateStatus(
                                """
                                Gmail conversation backup

                                Conversations checked
                                ${String.format("%,d", current)} of ${String.format("%,d", total)}

                                Uploaded or updated
                                ${String.format("%,d", uploaded)}

                                Unchanged
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
                            summary.totalConversations > 0 &&
                            !summary.stoppedAtSafetyLimit
                        ) {
                            1f
                        } else if (
                            summary.totalConversations > 0
                        ) {
                            summary.checkedConversations
                                .toFloat() /
                                    summary.totalConversations
                                        .toFloat()
                        } else {
                            0f
                        }

                    val safetyMessage =
                        if (summary.stoppedAtSafetyLimit) {
                            """

                            Test limit reached.
                            Only ${String.format("%,d", summary.uploadedConversations)} changed conversations were uploaded.
                            """.trimIndent()
                        } else {
                            ""
                        }

                    val failureDetails =
                        if (summary.failures.isNotEmpty()) {
                            """

                            First errors
                            ${summary.failures.joinToString("\n")}
                            """.trimIndent()
                        } else {
                            ""
                        }

                    val warningDetails =
                        if (summary.warnings.isNotEmpty()) {
                            """

                            Warnings
                            ${summary.warnings.joinToString("\n")}
                            """.trimIndent()
                        } else {
                            ""
                        }

                    updateStatus(
                        """
                        Gmail conversation backup completed

                        SMS on device
                        ${String.format("%,d", summary.totalMessages)}

                        Conversations on device
                        ${String.format("%,d", summary.totalConversations)}

                        Conversations checked
                        ${String.format("%,d", summary.checkedConversations)}

                        Uploaded or updated
                        ${String.format("%,d", summary.uploadedConversations)}

                        Unchanged
                        ${String.format("%,d", summary.skippedConversations)}

                        Failed
                        ${String.format("%,d", summary.failedConversations)}$safetyMessage$failureDetails$warningDetails
                        """.trimIndent()
                    )
                }

                result.onFailure { error ->
                    handleGmailError(
                        error = error,
                        failureTitle =
                            "Gmail conversation backup failed"
                    )
                }

            } catch (cancellation: CancellationException) {
                updateStatus(
                    gmailCancellationSummary(
                        checked = completed,
                        total = lastTotal,
                        uploaded = lastUploaded,
                        skipped = lastSkipped,
                        failed = lastFailed
                    )
                )
            } catch (error: Exception) {
                handleGmailError(
                    error = error,
                    failureTitle =
                        "Gmail conversation backup failed"
                )

            } finally {
                activeProfileId?.let(
                    GmailBackupSession::end
                )
                isGmailBackingUp = false
                isGmailBackupCancellationRequested = false
                activeGmailBackupJob = null
            }
        }
    }

    fun cancelGmailBackup() {
        val job = activeGmailBackupJob

        if (job?.isActive != true) {
            return
        }

        if (!isGmailBackupCancellationRequested) {
            isGmailBackupCancellationRequested = true
            updateStatus("Cancelling Gmail backup…")
        }

        job.cancel()
    }

    private fun gmailCancellationSummary(
        checked: Int,
        total: Int,
        uploaded: Int,
        skipped: Int,
        failed: Int
    ): String =
        """
        Gmail backup cancelled.

        Conversations checked: ${String.format("%,d", checked)} of ${String.format("%,d", total)}
        Uploaded or updated: ${String.format("%,d", uploaded)}
        Unchanged: ${String.format("%,d", skipped)}
        Failed: ${String.format("%,d", failed)}
        """.trimIndent()

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
            $failureTitle

            ${error.javaClass.name}

            Message
            ${error.message ?: "Unknown error"}
            """.trimIndent()
        )
    }

    private suspend fun resolveSelectedProfile(
        context: Context
    ): AccountProfileEntity? {
        val profile =
            GmailAccountManager(context)
                .getSelectedAccountProfile()

        if (profile == null) {
            accountStatus =
                "No Gmail account configured."

            updateStatus(
                "No Gmail account selected. Open Settings to add or select an account."
            )

            return null
        }

        updateAccountStatus(profile)

        if (
            profile.connectionState ==
            AccountProfileEntity
                .CONNECTION_STATE_AUTHORIZATION_REQUIRED
        ) {
            updateStatus(
                "Re-authorizing selected Gmail account..."
            )

            val authorizedProfile =
                GmailAccountCoordinator(context)
                    .authorizeAccount(profile)
                    .getOrElse {
                        updateAccountStatus(
                            profile.copy(
                                connectionState =
                                    AccountProfileEntity
                                        .CONNECTION_STATE_AUTHORIZATION_REQUIRED
                            )
                        )

                        updateStatus(
                            "Authorization is required. Open Settings to re-authorize this account."
                        )

                        return null
                    }

            updateAccountStatus(
                authorizedProfile
            )

            return authorizedProfile
        }

        if (
            profile.connectionState !=
            AccountProfileEntity
                .CONNECTION_STATE_CONNECTED
        ) {
            updateStatus(
                "No Gmail account selected. Open Settings to add or select an account."
            )

            return null
        }

        return profile
    }

    private fun updateAccountStatus(
        profile: AccountProfileEntity?
    ) {
        accountStatus =
            when (profile?.connectionState) {
                AccountProfileEntity
                    .CONNECTION_STATE_CONNECTED ->
                    "Connected: ${profile.accountEmail}"

                AccountProfileEntity
                    .CONNECTION_STATE_AUTHORIZATION_REQUIRED ->
                    "Authorization required for ${profile.accountEmail}"

                else ->
                    "No Gmail account configured."
            }
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
