package io.github.isht1008.opensmsbackup.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.account.GmailAccountManager
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.restore.*
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class RestoreViewModel(application: Application) : AndroidViewModel(application) {
    var accounts by mutableStateOf<List<AccountProfileEntity>>(emptyList()); private set
    var selectedAccount by mutableStateOf<AccountProfileEntity?>(null); private set
    var devices by mutableStateOf<List<RestoreSourceDevice>>(emptyList()); private set
    var selectedDevice by mutableStateOf<RestoreSourceDevice?>(null); private set
    var preview by mutableStateOf<RestorePreview?>(null); private set
    var selectedKeys by mutableStateOf<Set<String>>(emptySet()); private set
    var search by mutableStateOf("")
    var status by mutableStateOf("Select a Gmail account to discover restore sources."); private set
    var loading by mutableStateOf(false); private set
    var workId by mutableStateOf<UUID?>(null); private set
    var result by mutableStateOf<RestoreResult?>(null); private set
    var resultAccount by mutableStateOf<String?>(null); private set
    var resultDevice by mutableStateOf<String?>(null); private set

    val filteredConversations get() = preview?.conversations.orEmpty().filter {
        search.isBlank() || it.searchableText.contains(search.trim().lowercase())
    }

    init { viewModelScope.launch {
        accounts = GmailAccountManager(getApplication()).observeAccountProfiles().first()
            .filter { it.connectionState == AccountProfileEntity.CONNECTION_STATE_CONNECTED }
    }; observeWork() }

    fun chooseAccount(account: AccountProfileEntity) { selectedAccount = account; discoverDevices() }
    fun chooseDevice(device: RestoreSourceDevice) { selectedDevice = device; loadPreview() }
    fun toggle(key: String) { selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key }
    fun selectAll(selected: Boolean) { selectedKeys = if (selected) preview?.conversations.orEmpty().map { it.key }.toSet() else emptySet() }

    private fun repository(account: AccountProfileEntity) = GmailRestoreSourceRepository(
        GmailApiClient(getApplication()).createService(account), account.profileId, account.accountEmail
    )

    private fun discoverDevices() = viewModelScope.launch {
        val account = selectedAccount ?: return@launch
        loading = true; status = "Discovering device backups…"
        repository(account).discoverDevices().onSuccess {
            devices = it; status = if (it.isEmpty()) "No valid device backups found." else "Select a source device."
        }.onFailure { status = "Could not discover restore sources." }
        loading = false
    }

    private fun loadPreview() = viewModelScope.launch {
        val account = selectedAccount ?: return@launch
        val device = selectedDevice ?: return@launch
        loading = true; status = "Building restore preview…"
        repository(account).loadConversations(device).onSuccess { conversations ->
            val region = DeviceProfileStore.create(getApplication()).getOrCreate().defaultRegion
            val local = SmsRepository().getSmsMessages(getApplication(), false)
            val index = RestoreDuplicateIndex(local, region)
            val duplicates = conversations.sumOf { c -> c.snapshot.messages.count(index::contains) }
            preview = RestorePreview(account.profileId, account.accountEmail, device, conversations, duplicates)
            selectedKeys = conversations.map { it.key }.toSet()
            status = "Review the conversations before restoring."
        }.onFailure { status = "Could not load valid conversation snapshots." }
        loading = false
    }

    fun beginRestore(onRoleRequired: (android.content.Intent) -> Unit) = viewModelScope.launch {
        if (selectedKeys.isEmpty()) { status = "Select at least one conversation."; return@launch }
        val gate = AndroidRestoreRoleGate(getApplication())
        if (gate.isGranted()) enqueue() else onRoleRequired(gate.requestIntent())
    }

    fun onRoleResult(granted: Boolean) { if (granted) viewModelScope.launch { enqueue() }
        else status = "SMS role was not granted. Nothing was restored." }

    private suspend fun enqueue() {
        if (!AndroidRestoreRoleGate(getApplication()).isGranted()) { status = "SMS role is required."; return }
        val value = preview ?: return
        val region = DeviceProfileStore.create(getApplication()).getOrCreate().defaultRegion
        val messages = value.conversations.filter { it.key in selectedKeys }.flatMap { it.snapshot.messages }
        workId = RestoreWorkCoordinator(getApplication()).enqueue(
            RestorePlan(value.profileId, value.accountEmail, value.sourceDevice.displayName, region, messages)
        )
        status = "Restore queued. You can cancel from the progress notification."
    }

    private fun observeWork() = viewModelScope.launch {
        RestoreWorkCoordinator(getApplication()).observe().collect { infos ->
            val info = workId?.let { id -> infos.firstOrNull { it.id == id } }
                ?: infos.maxByOrNull { RestoreWorkContract.createdAt(it.tags) }
                ?: return@collect
            workId = info.id
            RestoreResultStore(getApplication()).readSource(info.id)?.let {
                resultAccount = it.first; resultDevice = it.second
            }
            val data = if (info.state.isFinished) info.outputData else info.progress
            result = if (info.state == androidx.work.WorkInfo.State.CANCELLED) {
                RestoreResultStore(getApplication()).read(info.id)?.copy(cancelled = true)
            } else RestoreWorkContract.result(data)
            status = when {
                info.state == androidx.work.WorkInfo.State.CANCELLED -> "Restore cancelled after partial progress."
                info.state == androidx.work.WorkInfo.State.FAILED -> "Restore could not start. No success is claimed."
                info.state.isFinished -> "Restore finished. Return your preferred messaging app to default."
                else -> "Restore in progress…"
            }
        }
    }

    fun cancel() { workId?.let { RestoreWorkCoordinator(getApplication()).cancel(it) } }
}
