package io.github.isht1008.opensmsbackup.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.isht1008.opensmsbackup.backup.BackupManager

class HomeViewModel : ViewModel() {

    var status by mutableStateOf("Ready")
        private set

    var isBackingUp by mutableStateOf(false)
        private set

    fun updateStatus(newStatus: String) {
        status = newStatus
    }

    fun startBackup(
        context: Context,
        includeContactNames: Boolean
    ) {

        if (isBackingUp) {
            return
        }

        isBackingUp = true

        updateStatus("Reading SMS...")

        viewModelScope.launch {

            try {

                val result = withContext(Dispatchers.IO) {

                    val backupManager = BackupManager()

                    backupManager.createBackup(
                        context = context,
                        includeContactNames = includeContactNames
                    )

                }

                updateStatus(
                    """
Read ${result.totalMessages} SMS

${result.totalConversations} Conversations

First:
${result.conversations.firstOrNull()?.messages?.firstOrNull()?.body}
                """.trimIndent()
                )

            } catch (e: Exception) {

                updateStatus(
                    "ERROR\n${e.javaClass.simpleName}\n${e.message}"
                )

            } finally {

                isBackingUp = false

            }
        }
    }
}