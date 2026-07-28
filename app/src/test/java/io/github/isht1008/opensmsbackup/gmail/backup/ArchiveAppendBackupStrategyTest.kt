package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfile
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploadResult
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ArchiveAppendBackupStrategyTest {
    @Test fun `deleted messages remain and new messages append in uploaded snapshot`() = runBlocking {
        val archived = conversation(message(1, 100, "archived"))
        val phone = conversation(message(2, 200, "new"))
        var uploadedEmail: SmsEmail? = null
        var persisted: ConversationSnapshotEntity? = null
        val strategy = strategy(
            archived = archived,
            onUpload = { uploadedEmail = it },
            onPersist = { persisted = it }
        )

        strategy.execute(
            phone, placeholderEmail(), "phone-hash", existingSnapshot(), {},
            LocalConversationSourceHashGenerator.generate(phone)
        )
            .getOrThrow()

        val attachment = requireNotNull(uploadedEmail).attachments.single()
        val merged = ArchivedConversationParser().parse(
            String(attachment.content, StandardCharsets.UTF_8)
        )
        assertEquals(listOf("archived", "new"), merged.messages.map { it.body })
        assertEquals(2, persisted?.messageCount)
        assertEquals("new-message", persisted?.gmailMessageId)
        assertEquals(
            LocalConversationSourceHashGenerator.generate(phone),
            persisted?.localSourceHash
        )
    }

    @Test fun `archive with no new phone messages does not upload`() = runBlocking {
        val archived = conversation(
            message(1, 100, "deleted"),
            message(2, 200, "keep")
        )
        val phone = conversation(message(2, 200, "keep"))
        var uploads = 0
        var persisted: ConversationSnapshotEntity? = null
        val strategy = strategy(
            archived,
            onUpload = { uploads++ },
            onPersist = { persisted = it }
        )

        val result = strategy.execute(
            phone, placeholderEmail(), "phone-hash", existingSnapshot(), {},
            LocalConversationSourceHashGenerator.generate(phone)
        ).getOrThrow()

        assertEquals(0, uploads)
        assertFalse(result.wasUploaded)
        assertEquals("old-message", result.messageId)
        assertEquals(
            LocalConversationSourceHashGenerator.generate(phone),
            persisted?.localSourceHash
        )
        val nextRun = ArchiveLocalCheckpointClassifier.classify(
            listOf(phone),
            mapOf(phone.threadId to requireNotNull(persisted)),
            "account-id",
            DEVICE_ID
        )
        assertEquals(1, nextRun.locallyUnchanged.size)
    }

    @Test fun `valid recovered archive updates stale Room cache without uploading`() = runBlocking {
        val archived = conversation(message(1, 100, "keep"))
        val phone = conversation(message(99, 100, "keep"))
        var persisted: ConversationSnapshotEntity? = null
        var uploads = 0
        val strategy = ArchiveAppendBackupStrategy(
            locateArchive = { _, _ ->
                Result.success(
                    GmailArchiveDocument(
                        "recovered-message",
                        "recovered-thread",
                        200,
                        "user@example.com",
                        null,
                        archived
                    )
                )
            },
            uploadConversation = {
                uploads++
                Result.success(GmailUploadResult("new", "new", emptyList()))
            },
            persistSnapshot = { persisted = it },
            accountId = "account-id",
            accountEmail = "user@example.com"
        )

        val result = strategy.execute(
            phone, placeholderEmail(), "phone-hash", existingSnapshot(), {}
        ).getOrThrow()

        assertEquals(0, uploads)
        assertFalse(result.wasUploaded)
        assertEquals("recovered-message", persisted?.gmailMessageId)
        assertEquals("recovered-thread", persisted?.gmailThreadId)
    }

    @Test fun `failed upload does not advance local source checkpoint`() = runBlocking {
        val archived = conversation(message(1, 100, "archived"))
        val phone = conversation(message(2, 200, "new"))
        val old = existingSnapshot().copy(localSourceHash = "old-local")
        var persisted: ConversationSnapshotEntity? = null
        val strategy = ArchiveAppendBackupStrategy(
            locateArchive = { _, _ ->
                Result.success(
                    GmailArchiveDocument(
                        "old-message", "old-thread", 100,
                        "user@example.com", null, archived
                    )
                )
            },
            uploadConversation = { Result.failure(IllegalStateException("upload failed")) },
            persistSnapshot = { persisted = it },
            accountId = "account-id",
            accountEmail = "user@example.com"
        )

        val result = strategy.execute(
            phone,
            placeholderEmail(),
            "phone-hash",
            old,
            {},
            LocalConversationSourceHashGenerator.generate(phone)
        )

        assertEquals(true, result.isFailure)
        assertEquals(null, persisted)
    }

    @Test fun `changed archive uploads before persisting merged checkpoint`() = runBlocking {
        val events = mutableListOf<String>()
        val archived = conversation(message(1, 100, "archived"))
        val phone = conversation(message(2, 200, "new"))
        val strategy = ArchiveAppendBackupStrategy(
            locateArchive = { _, _ ->
                Result.success(
                    GmailArchiveDocument(
                        "old-message", "old-thread", 100,
                        "user@example.com", null, archived
                    )
                )
            },
            uploadConversation = {
                events += "upload"
                Result.success(GmailUploadResult("new-message", "new-thread", emptyList()))
            },
            persistSnapshot = { events += "persist" },
            accountId = "account-id",
            accountEmail = "user@example.com"
        )

        strategy.execute(
            phone, placeholderEmail(), "phone-hash", existingSnapshot(), {},
            LocalConversationSourceHashGenerator.generate(phone)
        ).getOrThrow()

        assertEquals(listOf("upload", "persist"), events)
    }

    @Test fun `Room failure after upload never records a completed checkpoint`() = runBlocking {
        val events = mutableListOf<String>()
        val phone = conversation(message(2, 200, "new"))
        val strategy = ArchiveAppendBackupStrategy(
            locateArchive = { _, _ -> Result.success(null) },
            uploadConversation = {
                events += "upload"
                Result.success(GmailUploadResult("new-message", "new-thread", emptyList()))
            },
            persistSnapshot = {
                events += "persist-failed"
                error("Room failed")
            },
            accountId = "account-id",
            accountEmail = "user@example.com"
        )

        val failure = runCatching {
                strategy.execute(
                    phone, placeholderEmail(), "phone-hash", null, {},
                    LocalConversationSourceHashGenerator.generate(phone)
                )
            }.exceptionOrNull()
        assertTrue(failure is SnapshotPersistenceAfterUploadException)
        assertEquals(listOf("upload", "persist-failed"), events)
    }

    @Test fun `cancellation during upload does not persist checkpoint`() = runBlocking {
        var persisted = false
        val phone = conversation(message(2, 200, "new"))
        val strategy = ArchiveAppendBackupStrategy(
            locateArchive = { _, _ -> Result.success(null) },
            uploadConversation = { throw CancellationException("cancelled") },
            persistSnapshot = { persisted = true },
            accountId = "account-id",
            accountEmail = "user@example.com"
        )

        assertTrue(
            runCatching {
                strategy.execute(
                    phone, placeholderEmail(), "phone-hash", null, {},
                    LocalConversationSourceHashGenerator.generate(phone)
                )
            }.exceptionOrNull() is CancellationException
        )
        assertEquals(false, persisted)
    }

    private fun strategy(
        archived: SmsConversationSnapshot,
        onUpload: (SmsEmail) -> Unit = {},
        onPersist: (ConversationSnapshotEntity) -> Unit = {}
    ) = ArchiveAppendBackupStrategy(
        locateArchive = { _, _ ->
            Result.success(
                GmailArchiveDocument(
                    messageId = "old-message",
                    threadId = "old-thread",
                    internalDate = 100,
                    accountEmail = "user@example.com",
                    conversationKeyHeader = null,
                    conversation = archived
                )
            )
        },
        uploadConversation = {
            onUpload(it)
            Result.success(GmailUploadResult("new-message", "new-thread", listOf("label")))
        },
        persistSnapshot = { onPersist(it) },
        accountId = "account-id",
        accountEmail = "user@example.com",
        deviceProfile = deviceProfile()
    )

    private fun conversation(vararg messages: SmsMessage) =
        SmsConversationSnapshot(7, "+1 555 123 4567", "Contact", messages.toList())

    private fun message(id: Long, date: Long, body: String) =
        SmsMessage(id, 7, "+1 555 123 4567", "Contact", body, date, "date", 1)

    private fun existingSnapshot() = ConversationSnapshotEntity(
        accountId = "account-id",
        accountEmail = "user@example.com",
        androidThreadId = 7,
        address = "+1 555 123 4567",
        messageCount = 1,
        snapshotHash = "old-hash",
        gmailMessageId = "old-message",
        gmailThreadId = "old-thread",
        firstMessageDate = 100,
        lastMessageDate = 100
    )

    private fun placeholderEmail() = ConversationMimeMessageBuilder().build(
        conversation(), "user@example.com", "hash"
    )

    private fun deviceProfile() = DeviceProfile(
        deviceId = DEVICE_ID,
        manufacturer = "manufacturer",
        model = "model",
        marketingName = null,
        androidVersion = "test",
        primaryPhoneNumber = null,
        secondaryPhoneNumber = null,
        displayName = "Test device",
        createdAt = 1,
        updatedAt = 1
    )

    private companion object {
        const val DEVICE_ID = "device-a"
    }
}
