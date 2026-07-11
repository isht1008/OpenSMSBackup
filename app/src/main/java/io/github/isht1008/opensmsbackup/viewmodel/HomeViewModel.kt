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

    var progress by mutableStateOf(0f)
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

        progress = 0f

        updateStatus("Preparing backup...")

        viewModelScope.launch {

            try {

                val result = withContext(Dispatchers.IO) {

                    BackupManager().createBackup(
                        context = context,
                        includeContactNames = includeContactNames,
                        onProgress = { current, total ->

                            progress = current.toFloat() / total

                            val percent =
                                (progress * 100).toInt()

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