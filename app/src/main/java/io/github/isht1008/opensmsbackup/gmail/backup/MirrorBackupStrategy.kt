package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploadResult
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploader

class MirrorBackupStrategy(
    private val uploadConversation: suspend (SmsEmail) -> Result<GmailUploadResult>,
    private val trashMessage: suspend (String) -> Result<Unit>,
    private val persistSnapshot: suspend (ConversationSnapshotEntity) -> Unit,
    private val accountId: String,
    private val accountEmail: String,
    private val canTrashPrevious: suspend (
        String,
        SmsConversationSnapshot
    ) -> Boolean = { _, _ -> true },
    private val onPreviousSnapshotTrashed: (String) -> Unit = {}
) : BackupStrategy {
    constructor(
        uploader: GmailUploader,
        snapshotDao: io.github.isht1008.opensmsbackup.database.ConversationSnapshotDao,
        accountId: String,
        accountEmail: String,
        canTrashPrevious: suspend (String, SmsConversationSnapshot) -> Boolean = { _, _ -> true },
        onPreviousSnapshotTrashed: (String) -> Unit = {}
    ) : this(
        uploadConversation = uploader::uploadConversation,
        trashMessage = uploader::trashMessage,
        persistSnapshot = { snapshotDao.insert(it) },
        accountId = accountId,
        accountEmail = accountEmail,
        canTrashPrevious = canTrashPrevious,
        onPreviousSnapshotTrashed = onPreviousSnapshotTrashed
    )

    override suspend fun execute(
        conversation: SmsConversationSnapshot,
        email: SmsEmail?,
        snapshotHash: String,
        existingSnapshot: ConversationSnapshotEntity?,
        onPreviousSnapshotTrashFailure: suspend (Throwable) -> Unit,
        localSourceHash: String?
    ): Result<GmailUploadResult> {
        val uploadResult = uploadConversation(
            requireNotNull(email) { "Mirror backup requires a MIME message." }
        )
        val gmailResult = uploadResult.getOrNull() ?: return uploadResult

        try {
            persistSnapshot(
                ConversationSnapshotEntity(
                id = existingSnapshot?.id ?: 0L,
                accountId = accountId,
                accountEmail = accountEmail,
                androidThreadId = conversation.threadId,
                address = conversation.address.orEmpty(),
                contactName = conversation.contactName,
                messageCount = conversation.messageCount,
                snapshotHash = snapshotHash,
                gmailMessageId = gmailResult.messageId,
                gmailThreadId = gmailResult.threadId,
                firstMessageDate = conversation.firstMessageDate,
                lastMessageDate = conversation.lastMessageDate
                )
            )
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw SnapshotPersistenceAfterUploadException(error)
        }

        existingSnapshot?.gmailMessageId
            ?.takeIf { it.isNotBlank() && it != gmailResult.messageId }
            ?.let { oldMessageId ->
                if (canTrashPrevious(oldMessageId, conversation)) {
                    trashMessage(oldMessageId)
                        .onSuccess { onPreviousSnapshotTrashed(oldMessageId) }
                        .onFailure { onPreviousSnapshotTrashFailure(it) }
                }
            }

        return uploadResult
    }
}
