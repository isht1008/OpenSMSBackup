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
    private val profileId: String = "",
    private val deviceProfile: DeviceProfile? = null,
    private val merger: ArchiveConversationMerger = ArchiveConversationMerger(),
    private val emailBuilder: ConversationMimeMessageBuilder = ConversationMimeMessageBuilder(),
    private val onInserted: suspend (
        SmsConversationSnapshot,
        GmailUploadResult,
        Long
    ) -> Unit = { _, _, _ -> },
    private val onUploading: suspend () -> Unit = {},
    private val onTiming: (operation: String, durationMillis: Long, success: Boolean) -> Unit =
        { _, _, _ -> }
) : BackupStrategy {
    constructor(
        locator: GmailArchiveLocator,
        uploader: GmailUploader,
        snapshotDao: ConversationSnapshotDao,
        accountId: String,
        accountEmail: String,
        profileId: String,
        deviceProfile: DeviceProfile,
        onInserted: suspend (SmsConversationSnapshot, GmailUploadResult, Long) -> Unit =
            { _, _, _ -> },
        onUploading: suspend () -> Unit = {},
        onTiming: (operation: String, durationMillis: Long, success: Boolean) -> Unit =
            { _, _, _ -> }
    ) : this(
        locateArchive = locator::locate,
        uploadConversation = uploader::uploadConversation,
        persistSnapshot = { snapshotDao.insert(it) },
        profileId = profileId,
        accountId = accountId,
        accountEmail = accountEmail,
        deviceProfile = deviceProfile,
        merger = ArchiveConversationMerger(deviceProfile.defaultRegion),
        onInserted = onInserted,
        onUploading = onUploading,
        onTiming = onTiming
    )

    override suspend fun execute(
        conversation: SmsConversationSnapshot,
        email: SmsEmail?,
        snapshotHash: String,
        existingSnapshot: ConversationSnapshotEntity?,
        onPreviousSnapshotTrashFailure: suspend (Throwable) -> Unit,
        localSourceHash: String?
    ): Result<GmailUploadResult> {
        val locateStarted = monotonicMillis()
        val locatedResult = locateArchive(
            conversation,
            existingSnapshot?.gmailMessageId
        )
        onTiming(
            "archive_locate",
            monotonicMillis() - locateStarted,
            locatedResult.isSuccess
        )
        val located = locatedResult.getOrElse {
            return Result.failure(it)
        }
        val archived = located?.conversation
        val mergeStarted = monotonicMillis()
        val merge = merger.merge(archived, conversation).also {
            onTiming(
                "fingerprint_merge",
                monotonicMillis() - mergeStarted,
                true
            )
        }

        if (located != null && merge.appendedCount == 0) {
            val persistStarted = monotonicMillis()
            persistSnapshot(
                recoveredSnapshot(
                    conversation = conversation,
                    archived = located,
                    existingSnapshot = existingSnapshot,
                    localSourceHash = localSourceHash
                )
            )
            onTiming(
                "room_persist_noop",
                monotonicMillis() - persistStarted,
                true
            )
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
        val mimeStarted = monotonicMillis()
        val mergedEmail = emailBuilder.build(
            conversation = merge.conversation,
            accountEmail = accountEmail,
            snapshotHash = mergedHash,
            deviceProfile = deviceProfile
        ).also {
            onTiming(
                "mime_generation",
                monotonicMillis() - mimeStarted,
                true
            )
        }
        onUploading()
        val uploadStarted = monotonicMillis()
        val uploadResult = uploadConversation(mergedEmail)
        onTiming(
            "gmail_upload",
            monotonicMillis() - uploadStarted,
            uploadResult.isSuccess
        )
        val gmailResult = uploadResult.getOrNull() ?: return uploadResult

        try {
            val persistStarted = monotonicMillis()
            persistSnapshot(
                ConversationSnapshotEntity(
                id = existingSnapshot?.id ?: 0L,
                profileId = profileId,
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
                lastMessageDate = merge.conversation.lastMessageDate,
                localSourceHash = localSourceHash,
                localSourceMessageCount = conversation.messageCount,
                localSourceLastMessageDate = conversation.lastMessageDate,
                localSourceMaxSmsId = conversation.messages.maxOfOrNull { it.id } ?: 0L,
                localSourceDeviceId = deviceProfile?.deviceId
                )
            )
            onTiming(
                "room_persist_upload",
                monotonicMillis() - persistStarted,
                true
            )
        } catch (error: Throwable) {
            onTiming("room_persist_upload", 0L, false)
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw SnapshotPersistenceAfterUploadException(error)
        }
        onInserted(merge.conversation, gmailResult, mergedEmail.date)

        return uploadResult
    }

    private fun recoveredSnapshot(
        conversation: SmsConversationSnapshot,
        archived: GmailArchiveDocument,
        existingSnapshot: ConversationSnapshotEntity?,
        localSourceHash: String?
    ): ConversationSnapshotEntity = ConversationSnapshotEntity(
        id = existingSnapshot?.id ?: 0L,
        profileId = profileId,
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
        lastMessageDate = archived.conversation.lastMessageDate,
        localSourceHash = localSourceHash ?: existingSnapshot?.localSourceHash,
        localSourceMessageCount = if (localSourceHash != null) conversation.messageCount
            else existingSnapshot?.localSourceMessageCount,
        localSourceLastMessageDate = if (localSourceHash != null) conversation.lastMessageDate
            else existingSnapshot?.localSourceLastMessageDate,
        localSourceMaxSmsId = if (localSourceHash != null) {
            conversation.messages.maxOfOrNull { it.id } ?: 0L
        } else {
            existingSnapshot?.localSourceMaxSmsId
        },
        localSourceDeviceId = if (localSourceHash != null) {
            deviceProfile?.deviceId
        } else {
            existingSnapshot?.localSourceDeviceId
        }
    )

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
}
