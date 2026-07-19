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
        val archive = ArchiveAppendBackupStrategy(mirror)

        assertSame(
            mirror,
            BackupStrategySelector(mirror, archive).select(GmailBackupMode.MIRROR)
        )
    }

    @Test fun `archive append mode selects archive strategy`() {
        val mirror = mirrorStrategy()
        val archive = ArchiveAppendBackupStrategy(mirror)

        assertSame(
            archive,
            BackupStrategySelector(mirror, archive)
                .select(GmailBackupMode.ARCHIVE_APPEND_ONLY)
        )
    }

    @Test fun `archive strategy currently delegates to mirror`() = runBlocking {
        var receivedEmail: SmsEmail? = null
        val mirror = object : BackupStrategy {
            override suspend fun execute(
                conversation: SmsConversationSnapshot,
                email: SmsEmail,
                snapshotHash: String,
                existingSnapshot: ConversationSnapshotEntity?,
                onPreviousSnapshotTrashFailure: suspend (Throwable) -> Unit
            ): Result<GmailUploadResult> {
                receivedEmail = email
                return Result.success(uploadResult)
            }
        }
        val email = email()

        val result = ArchiveAppendBackupStrategy(mirror).execute(
            conversation(), email, "hash", null, {}
        )

        assertSame(email, receivedEmail)
        assertEquals(uploadResult, result.getOrThrow())
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

    private fun mirrorStrategy() =
        MirrorBackupStrategy(
            uploadConversation = { Result.success(uploadResult) },
            trashMessage = { Result.success(Unit) },
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
}
