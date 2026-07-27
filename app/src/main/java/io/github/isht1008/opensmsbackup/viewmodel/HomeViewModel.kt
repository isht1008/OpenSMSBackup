package io.github.isht1008.opensmsbackup.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import io.github.isht1008.opensmsbackup.backup.BackupHistoryRepository
import io.github.isht1008.opensmsbackup.backup.BackupManager
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeController
import io.github.isht1008.opensmsbackup.account.data.GmailBackupModeUiState
import io.github.isht1008.opensmsbackup.account.data.MultiAccountRepository
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountCoordinator
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.api.AndroidGmailConnectivityGateway
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.api.GmailConnectivityPreflight
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupEnqueueResult
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupPhase
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupUiStage
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupUiState
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkContract
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkStateMapper
import androidx.work.WorkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.verification.BackupVerificationWorkCoordinator
import io.github.isht1008.opensmsbackup.verification.BackupVerificationWorkContract
import java.util.UUID
import java.util.Locale

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val gmailWorkCoordinator =
        GmailBackupWorkCoordinator(application)
    private val verificationCoordinator = BackupVerificationWorkCoordinator(application)
    private val multiAccountRepository = MultiAccountRepository.create(application)
    private val backupModeController = GmailBackupModeController(
        multiAccountRepository
    )
    private val gmailConnectivityPreflight = GmailConnectivityPreflight(
        AndroidGmailConnectivityGateway(GmailApiClient(application))
    )

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

    var gmailBackupCompletion by mutableStateOf<GmailBackupCompletion?>(null)
        private set

    var gmailBackupUiState by mutableStateOf(GmailBackupUiState())
        private set

    var gmailBackupProgress by mutableStateOf(0f)
        private set
    var gmailBackupModeUiState by mutableStateOf(GmailBackupModeUiState())
        private set
    var selectedGmailProfile by mutableStateOf<AccountProfileEntity?>(null)
        private set
    var lastGmailBackupTime by mutableStateOf<Long?>(null)
        private set
    var latestVerification by mutableStateOf<BackupVerificationEntity?>(null)
        private set
    var verificationProgress by mutableStateOf<io.github.isht1008.opensmsbackup.verification.BackupVerificationProgress?>(null)
        private set
    var verificationWorkId by mutableStateOf<UUID?>(null)
        private set
    var isVerifying by mutableStateOf(false)
        private set

    var onGmailConsentRequired: ((Intent) -> Unit)? = null

    init {
        observeGmailBackupWork()
        observeVerification()
    }

    fun startVerification() = viewModelScope.launch {
        val profile = GmailAccountManager(getApplication()).getSelectedAccountProfile()
        if (profile == null) { status = "Select a connected Gmail account first."; return@launch }
        if (profile.connectionState != AccountProfileEntity.CONNECTION_STATE_CONNECTED) {
            status = "Reconnect the selected Gmail account before verification."
            return@launch
        }
        val device = DeviceProfileStore.create(getApplication()).getOrCreate()
        verificationWorkId = verificationCoordinator.enqueue(profile.profileId, device.deviceId)
        status = "Backup verification queued."
    }

    fun cancelVerification() { verificationWorkId?.let(verificationCoordinator::cancel) }

    private fun observeVerification() = viewModelScope.launch {
        verificationCoordinator.observe().collectLatest { infos ->
            val selected = infos.firstOrNull { !it.state.isFinished }
                ?: infos.maxByOrNull { BackupVerificationWorkContract.createdAt(it.tags) }
            verificationWorkId = selected?.id
            isVerifying = selected?.state?.let { !it.isFinished } == true
            verificationProgress = selected?.let { BackupVerificationWorkContract.readProgress(it.progress) }
            if (selected?.state?.isFinished == true) loadLatestVerification()
        }
    }

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

            selectedGmailProfile = profile
            updateAccountStatus(profile)
            refreshGmailBackupMode(profile)
            loadLastGmailBackup(profile)
            loadLatestVerification(profile)
        }
    }

    private suspend fun loadLastGmailBackup(profile: AccountProfileEntity?) {
        lastGmailBackupTime = profile?.let {
            DatabaseProvider.getDatabase(getApplication())
                .backupAccountDao()
                .findByEmail(it.accountEmail.trim().lowercase(Locale.ROOT))
                ?.lastBackupTime
                ?.takeIf { time -> time > 0L }
        }
    }

    private suspend fun refreshGmailBackupMode(profile: AccountProfileEntity?) {
        gmailBackupModeUiState = GmailBackupModeUiState(
            profileId = profile?.profileId,
            isLoading = profile != null
        )
        backupModeController.load(profile?.profileId)
        gmailBackupModeUiState = backupModeController.state
    }

    private suspend fun loadLatestVerification(
        profile: AccountProfileEntity? = null
    ) {
        val resolvedProfile = profile ?: GmailAccountManager(getApplication()).getSelectedAccountProfile()
        val device = DeviceProfileStore.create(getApplication()).getOrCreate()
        latestVerification = resolvedProfile?.let {
            DatabaseProvider.getDatabase(getApplication()).backupVerificationDao()
                .findLatest(it.profileId, device.deviceId)
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
                        .getProfile(
                            accountProfile
                        )

                result.onSuccess {
                    updateStatus(
                        "Gmail API connection confirmed. No mailbox messages were read or changed."
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
        notificationsEnabled: Boolean = true
    ) {
        if (gmailBackupUiState.isActive || isBackingUp) {
            updateStatus("A Gmail backup is already running.")
            return
        }
        gmailBackupUiState = GmailBackupUiState(
            stage = GmailBackupUiStage.ENQUEUING,
            phase = "Enqueuing Gmail backup"
        )
        isGmailBackingUp = true
        gmailBackupProgress = 0f
        gmailBackupCompletion = null
        viewModelScope.launch {
            val profile = resolveSelectedProfile(context)
            if (profile == null) {
                gmailBackupUiState = GmailBackupUiState()
                isGmailBackingUp = false
                return@launch
            }
            updateAccountStatus(profile)
            updateStatus("Testing Gmail API connection…")
            gmailConnectivityPreflight.check(profile).onFailure { error ->
                gmailBackupUiState = GmailBackupUiState()
                isGmailBackingUp = false
                handleGmailError(error, "Gmail API connection test failed")
                return@launch
            }
            updateStatus("Gmail API connection confirmed. Starting backup…")
            when (val result = gmailWorkCoordinator.enqueueManual(
                profileId = profile.profileId,
                includeContactNames = includeContactNames
            )) {
                is GmailBackupEnqueueResult.Enqueued -> {
                    gmailBackupUiState = gmailBackupUiState.copy(
                        workId = result.workId,
                        profileId = profile.profileId,
                        accountEmail = profile.accountEmail
                    )
                    updateStatus(
                        if (notificationsEnabled) {
                            "Gmail backup queued for ${profile.accountEmail}."
                        } else {
                            "Gmail backup queued. Notifications are disabled, so progress won't appear in the notification drawer."
                        }
                    )
                }
                is GmailBackupEnqueueResult.AlreadyRunning ->
                    run {
                        gmailBackupUiState = gmailBackupUiState.copy(
                            stage = GmailBackupUiStage.PREPARING,
                            workId = result.workId,
                            phase = "A Gmail backup is already running"
                        )
                        updateStatus("A Gmail backup is already running.")
                    }
            }
        }
    }

    fun cancelGmailBackup() {
        val workId = gmailBackupUiState.workId ?: return
        if (!gmailBackupUiState.isCancellable) return
        gmailBackupUiState = gmailBackupUiState.copy(
            stage = GmailBackupUiStage.CANCELLING,
            phase = "Cancelling Gmail backup…"
        )
        isGmailBackupCancellationRequested = true
        updateStatus("Cancelling Gmail backup…")
        gmailWorkCoordinator.cancel(workId)
    }

    private fun observeGmailBackupWork() {
        viewModelScope.launch {
            gmailWorkCoordinator.observeAll().collectLatest { workInfos ->
                val active = workInfos.firstOrNull {
                    GmailBackupWorkContract.isActive(it.state)
                }
                val terminal = workInfos.filter {
                    it.state.isFinished &&
                        (GmailBackupWorkContract.readCompletion(it.outputData) != null ||
                            it.state == WorkInfo.State.CANCELLED)
                }.maxByOrNull { GmailBackupWorkContract.createdAt(it.tags) }
                val selected = active ?: terminal ?: return@collectLatest
                applyGmailUiState(selected)
            }
        }
    }

    private fun applyGmailUiState(workInfo: WorkInfo) {
        val progress = GmailBackupWorkContract.readProgress(workInfo.progress)
        val completion = GmailBackupWorkContract.readCompletion(workInfo.outputData)
        gmailBackupUiState = GmailBackupWorkStateMapper.map(
            workId = workInfo.id,
            state = workInfo.state,
            progress = progress,
            completion = completion,
            previous = gmailBackupUiState
        )
        gmailBackupCompletion = completion
        isGmailBackingUp = gmailBackupUiState.isActive
        isGmailBackupCancellationRequested = gmailBackupUiState.stage == GmailBackupUiStage.CANCELLING
        gmailBackupProgress = gmailBackupUiState.fraction ?: 0f
        status = buildGmailWorkStatus(gmailBackupUiState)
        if (workInfo.state.isFinished && completion != null) {
            viewModelScope.launch {
                loadLastGmailBackup(selectedGmailProfile)
            }
        }
        android.util.Log.i(
            "OpenSMSBackup",
            "gmail_work_state_restored work=${workInfo.id} state=${workInfo.state}"
        )
    }

    private fun buildGmailWorkStatus(state: GmailBackupUiState): String =
        buildString {
            append(state.phase)
            state.accountEmail?.let { append("\n\nAccount\n$it") }
            if (state.total > 0) {
                append("\n\nChecked\n${String.format("%,d", state.checked)} / ${String.format("%,d", state.total)}")
                append("\n\nUploaded\n${String.format("%,d", state.uploaded)}")
                append("\n\nUnchanged\n${String.format("%,d", state.unchanged)}")
                append("\n\nFailed\n${String.format("%,d", state.failed)}")
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
