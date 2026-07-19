package io.github.isht1008.opensmsbackup.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.health.*
import io.github.isht1008.opensmsbackup.model.BackupHistoryItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.isht1008.opensmsbackup.verification.BackupVerificationWorkCoordinator

class BackupHealthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackupHealthRepository(
        DatabaseProvider.getDatabase(application).backupVerificationDao()
    )
    private val workCoordinator = BackupVerificationWorkCoordinator(application)
    var history by mutableStateOf<List<BackupVerificationEntity>>(emptyList()); private set
    var localBackups by mutableStateOf<List<BackupHistoryItem>>(emptyList()); private set
    var filter by mutableStateOf(BackupHealthFilter.ALL)
    var search by mutableStateOf("")
    var isLoading by mutableStateOf(true); private set
    var isVerificationRunning by mutableStateOf(false); private set

    val filteredHistory get() = BackupHealthCalculator.filter(history, filter, search)
    val statistics get() = BackupHealthCalculator.statistics(history)
    val latestVerification get() = BackupHealthCalculator.order(history).firstOrNull()
    val archiveHealth get() = BackupHealthCalculator.health(latestVerification)
    val latestLocalBackup get() = localBackups.firstOrNull()

    init {
        viewModelScope.launch {
            repository.observeVerificationHistory().collectLatest {
                history = it
                isLoading = false
            }
        }
        viewModelScope.launch { localBackups = repository.loadLocalBackups(application) }
        viewModelScope.launch {
            workCoordinator.observe().collectLatest { work ->
                isVerificationRunning = work.any { !it.state.isFinished }
            }
        }
    }
}

class BackupHealthDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = BackupHealthRepository(
        DatabaseProvider.getDatabase(application).backupVerificationDao()
    )
    var item by mutableStateOf<BackupVerificationEntity?>(null); private set
    var isLoading by mutableStateOf(true); private set
    init {
        viewModelScope.launch {
            item = savedStateHandle.get<String>("verificationId")?.toLongOrNull()
                ?.let { repository.findVerification(it) }
            isLoading = false
        }
    }
}
