package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.database.ConversationSnapshotDao
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploadResult
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploader
import io.github.isht1008.opensmsbackup.device.DeviceProfile

class ArchiveAppendBackupStrategy(
    private val locateArchive: suspend (
        SmsConversationSnapshot,
        String?
    ) -> Result<GmailArchiveDocument?>,
    private val uploadConversation: suspend (SmsEmail) -> Result<GmailUploadResult>,
    private val persistSnapshot: suspend (ConversationSnapshotEntity) -> Unit,
    private val accountId: String,
    private val accountEmail: String,
    private val deviceProfile: DeviceProfile? = null,
    private val merger: ArchiveConversationMerger = ArchiveConversationMerger(),
    private val emailBuilder: ConversationMimeMessageBuilder = ConversationMimeMessageBuilder()
) : BackupStrategy {
    constructor(
        locator: GmailArchiveLocator,
        uploader: GmailUploader,
        snapshotDao: ConversationSnapshotDao,
        accountId: String,
        accountEmail: String,
        deviceProfile: DeviceProfile
    ) : this(
        locateArchive = locator::locate,
        uploadConversation = uploader::uploadConversation,
        persistSnapshot = { snapshotDao.insert(it) },
        accountId = accountId,
        accountEmail = accountEmail,
        deviceProfile = deviceProfile,
        merger = ArchiveConversationMerger(deviceProfile.defaultRegion)
    )

    override suspend fun execute(
        conversation: SmsConversationSnapshot,
        email: SmsEmail,
        snapshotHash: String,
        existingSnapshot: ConversationSnapshotEntity?,
        onPreviousSnapshotTrashFailure: suspend (Throwable) -> Unit
    ): Result<GmailUploadResult> {
        val located = locateArchive(
            conversation,
            existingSnapshot?.gmailMessageId
        ).getOrElse {
            return Result.failure(it)
        }
        val archived = located?.conversation

        if (
            located != null &&
            (existingSnapshot == null ||
                existingSnapshot.gmailMessageId != located.messageId ||
                existingSnapshot.gmailThreadId != located.threadId)
        ) {
            persistSnapshot(
                recoveredSnapshot(
                    conversation = conversation,
                    archived = located,
                    existingSnapshot = existingSnapshot
                )
            )
        }

        val merge = merger.merge(archived, conversation)

        if (located != null && merge.appendedCount == 0) {
            return Result.success(
                GmailUploadResult(
                    messageId = located.messageId,
                    threadId = located.threadId,
                    labelIds = emptyList(),
                    wasUploaded = false
                )
            )
        }

        val mergedHash = ConversationSnapshotHashGenerator.generate(merge.conversation)
        val mergedEmail = emailBuilder.build(
            conversation = merge.conversation,
            accountEmail = accountEmail,
            snapshotHash = mergedHash,
            deviceProfile = deviceProfile
        )
        val uploadResult = uploadConversation(mergedEmail)
        val gmailResult = uploadResult.getOrNull() ?: return uploadResult

        persistSnapshot(
            ConversationSnapshotEntity(
                id = existingSnapshot?.id ?: 0L,
                accountId = accountId,
                accountEmail = accountEmail,
                androidThreadId = conversation.threadId,
                address = merge.conversation.address.orEmpty(),
                contactName = merge.conversation.contactName,
                messageCount = merge.conversation.messageCount,
                snapshotHash = mergedHash,
                gmailMessageId = gmailResult.messageId,
                gmailThreadId = gmailResult.threadId,
                firstMessageDate = merge.conversation.firstMessageDate,
                lastMessageDate = merge.conversation.lastMessageDate
            )
        )

        return uploadResult
    }

    private fun recoveredSnapshot(
        conversation: SmsConversationSnapshot,
        archived: GmailArchiveDocument,
        existingSnapshot: ConversationSnapshotEntity?
    ): ConversationSnapshotEntity = ConversationSnapshotEntity(
        id = existingSnapshot?.id ?: 0L,
        accountId = accountId,
        accountEmail = accountEmail,
        androidThreadId = conversation.threadId,
        address = archived.conversation.address.orEmpty(),
        contactName = archived.conversation.contactName,
        messageCount = archived.conversation.messageCount,
        snapshotHash = ConversationSnapshotHashGenerator.generate(archived.conversation),
        gmailMessageId = archived.messageId,
        gmailThreadId = archived.threadId,
        firstMessageDate = archived.conversation.firstMessageDate,
        lastMessageDate = archived.conversation.lastMessageDate
    )
}
