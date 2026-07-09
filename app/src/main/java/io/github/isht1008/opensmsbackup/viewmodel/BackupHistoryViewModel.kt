package io.github.isht1008.opensmsbackup.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.backup.BackupHistoryRepository
import io.github.isht1008.opensmsbackup.model.BackupHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupHistoryViewModel : ViewModel() {

    var backups by mutableStateOf<List<BackupHistoryItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun loadBackups(context: Context) {

        isLoading = true

        viewModelScope.launch {

            backups = withContext(Dispatchers.IO) {
                BackupHistoryRepository().getBackups(context)
            }

            isLoading = false
        }
    }
}