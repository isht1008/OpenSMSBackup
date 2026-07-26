package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStrategyTest {
    private val uploadResult = GmailUploadResult(
        messageId = "message-id",
        threadId = "thread-id",
        labelIds = listOf("label-id")
    )

    @Test fun `mirror mode selects mirror strategy`() {
        val mirror = mirrorStrategy()
        val archive = archiveStrategy()

        assertSame(
            mirror,
            BackupStrategySelector(mirror, archive).select(GmailBackupMode.MIRROR)
        )
    }

    @Test fun `archive append mode selects archive strategy`() {
        val mirror = mirrorStrategy()
        val archive = archiveStrategy()

        assertSame(
            archive,
            BackupStrategySelector(mirror, archive)
                .select(GmailBackupMode.ARCHIVE_APPEND_ONLY)
        )
    }

    @Test fun `mirror strategy preserves existing upload result`() = runBlocking {
        val expected = Result.success(uploadResult)
        var persisted: ConversationSnapshotEntity? = null
        val strategy = MirrorBackupStrategy(
            uploadConversation = { expected },
            trashMessage = { Result.success(Unit) },
            persistSnapshot = { persisted = it },
            accountId = "account-id",
            accountEmail = "account@example.com"
        )

        val actual = strategy.execute(
            conversation(), email(), "hash", null, {}
        )

        assertTrue(actual.isSuccess)
        assertEquals(expected.getOrThrow(), actual.getOrThrow())
        assertEquals(uploadResult.messageId, persisted?.gmailMessageId)
        assertEquals("hash", persisted?.snapshotHash)
    }

    @Test fun `mirror does not trash snapshot rejected as another device`() = runBlocking {
        var trashCalls = 0
        val strategy = MirrorBackupStrategy(
            uploadConversation = { Result.success(uploadResult) },
            trashMessage = { trashCalls++; Result.success(Unit) },
            persistSnapshot = {},
            accountId = "account-id",
            accountEmail = "account@example.com",
            canTrashPrevious = { _, _ -> false }
        )
        strategy.execute(conversation(), email(), "hash", existingSnapshot(), {}).getOrThrow()
        assertEquals(0, trashCalls)
    }

    @Test fun `same device mirror replacement still trashes previous snapshot`() = runBlocking {
        var trashCalls = 0
        val strategy = MirrorBackupStrategy(
            uploadConversation = { Result.success(uploadResult) },
            trashMessage = { trashCalls++; Result.success(Unit) },
            persistSnapshot = {},
            accountId = "account-id",
            accountEmail = "account@example.com",
            canTrashPrevious = { _, _ -> true }
        )
        strategy.execute(conversation(), email(), "hash", existingSnapshot(), {}).getOrThrow()
        assertEquals(1, trashCalls)
    }

    @Test fun `mirror trashes only after upload and persistence succeed`() = runBlocking {
        val events = mutableListOf<String>()
        val strategy = MirrorBackupStrategy(
            uploadConversation = { events += "upload"; Result.success(uploadResult) },
            trashMessage = { events += "trash"; Result.success(Unit) },
            persistSnapshot = { events += "persist" },
            accountId = "account-id",
            accountEmail = "account@example.com",
            canTrashPrevious = { _, _ -> true }
        )
        strategy.execute(conversation(), email(), "changed", existingSnapshot(), {}).getOrThrow()
        assertEquals(listOf("upload", "persist", "trash"), events)
    }

    @Test fun `failed mirror replacement leaves previous snapshot intact`() = runBlocking {
        var persisted = false
        var trashed = false
        val strategy = MirrorBackupStrategy(
            uploadConversation = { Result.failure(IllegalStateException("failed")) },
            trashMessage = { trashed = true; Result.success(Unit) },
            persistSnapshot = { persisted = true },
            accountId = "account-id",
            accountEmail = "account@example.com"
        )
        assertTrue(strategy.execute(conversation(), email(), "changed", existingSnapshot(), {}).isFailure)
        assertTrue(!persisted)
        assertTrue(!trashed)
    }

    private fun mirrorStrategy() =
        MirrorBackupStrategy(
            uploadConversation = { Result.success(uploadResult) },
            trashMessage = { Result.success(Unit) },
            persistSnapshot = {},
            accountId = "account-id",
            accountEmail = "account@example.com"
        )

    private fun archiveStrategy() = ArchiveAppendBackupStrategy(
        locateArchive = { _, _ -> Result.success(null) },
        uploadConversation = { Result.success(uploadResult) },
        persistSnapshot = {},
        accountId = "account-id",
        accountEmail = "account@example.com"
    )

    private fun conversation() = SmsConversationSnapshot(
        threadId = 7L,
        address = null,
        contactName = null,
        messages = emptyList()
    )

    private fun email() = SmsEmail(
        from = "sender@example.com",
        to = "recipient@example.com",
        subject = "subject",
        body = "body",
        date = 1L,
        headers = emptyMap()
    )

    private fun existingSnapshot() = ConversationSnapshotEntity(
        accountId = "account-id",
        accountEmail = "account@example.com",
        androidThreadId = 7,
        address = "",
        messageCount = 0,
        snapshotHash = "old",
        gmailMessageId = "old-message",
        firstMessageDate = 0,
        lastMessageDate = 0
    )
}
