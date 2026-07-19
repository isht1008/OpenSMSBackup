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
    private val accountEmail: String
) : BackupStrategy {
    constructor(
        uploader: GmailUploader,
        snapshotDao: io.github.isht1008.opensmsbackup.database.ConversationSnapshotDao,
        accountId: String,
        accountEmail: String
    ) : this(
        uploadConversation = uploader::uploadConversation,
        trashMessage = uploader::trashMessage,
        persistSnapshot = { snapshotDao.insert(it) },
        accountId = accountId,
        accountEmail = accountEmail
    )

    override suspend fun execute(
        conversation: SmsConversationSnapshot,
        email: SmsEmail,
        snapshotHash: String,
        existingSnapshot: ConversationSnapshotEntity?,
        onPreviousSnapshotTrashFailure: suspend (Throwable) -> Unit
    ): Result<GmailUploadResult> {
        val uploadResult = uploadConversation(email)
        val gmailResult = uploadResult.getOrNull() ?: return uploadResult

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

        existingSnapshot?.gmailMessageId
            ?.takeIf { it.isNotBlank() && it != gmailResult.messageId }
            ?.let { oldMessageId ->
                trashMessage(oldMessageId)
                    .onFailure { onPreviousSnapshotTrashFailure(it) }
            }

        return uploadResult
    }
}
