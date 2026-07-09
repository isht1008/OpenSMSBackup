package io.github.isht1008.opensmsbackup.model

import android.net.Uri

data class BackupHistoryItem(
    val displayName: String,
    val uri: Uri,
    val createdAt: String,
    val messageCount: Int,
    val conversationCount: Int,
    val fileSizeBytes: Long
)