package io.github.isht1008.opensmsbackup.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isht1008.opensmsbackup.backup.BackupHistoryRepository
import io.github.isht1008.opensmsbackup.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        updateStatus("Preparing backup...")

        viewModelScope.launch {

            try {

                val result = withContext(Dispatchers.IO) {

                    BackupManager().createBackup(
                        context = context,
                        includeContactNames = includeContactNames,
                        onProgress = { current, total ->

                            updateStatus(
                                """
Reading SMS...

$current / $total
                                """.trimIndent()
                            )
                        }
                    )

                }

                updateStatus(
                    """
✅ Backup completed

Messages:
${result.totalMessages}

Conversations:
${result.totalConversations}

First:
${result.conversations.firstOrNull()?.messages?.firstOrNull()?.body}
                    """.trimIndent()
                )

            } catch (e: Exception) {

                updateStatus(
                    """
ERROR

${e.javaClass.simpleName}

${e.message}
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

            } catch (e: Exception) {

                updateStatus(
                    """
Failed to load backups

${e.javaClass.simpleName}

${e.message}
                    """.trimIndent()
                )
            }
        }
    }
}