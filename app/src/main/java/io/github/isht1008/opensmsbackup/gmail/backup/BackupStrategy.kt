package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploadResult
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity

class SnapshotPersistenceAfterUploadException(cause: Throwable) :
    Exception("Snapshot persistence failed after Gmail upload.", cause)

interface BackupStrategy {
    suspend fun execute(
        conversation: SmsConversationSnapshot,
        email: SmsEmail?,
        snapshotHash: String,
        existingSnapshot: ConversationSnapshotEntity?,
        onPreviousSnapshotTrashFailure: suspend (Throwable) -> Unit,
        localSourceHash: String? = null
    ): Result<GmailUploadResult>
}
