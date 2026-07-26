package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupPhase
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.isht1008.opensmsbackup.sms.SmsMessage

class GmailBackupConversationLimiterTest {
    @Test fun `twenty conversations produce exactly ten recent candidates`() {
        val source = (1L..20L).map { conversation(it, "address-$it", latestDate = it) }
        val scope = GmailBackupConversationLimiter.applyConversations(source, 10)
        assertEquals((20L downTo 11L).toList(), scope.conversations.map { it.threadId })
        assertEquals(20, scope.sourceConversationCount)
        assertTrue(scope.isLimitedTest)
        assertEquals((1L..20L).toList(), source.map { it.threadId })
    }

    @Test fun `fewer than ten conversations include all available`() {
        val source = (1L..7L).map { conversation(it, "address-$it", latestDate = it) }
        val scope = GmailBackupConversationLimiter.applyConversations(source, 10)
        assertEquals((7L downTo 1L).toList(), scope.conversations.map { it.threadId })
        assertTrue(scope.isLimitedTest)
    }

    @Test fun `null limit preserves normal full behavior`() {
        val source = (1L..20L).map { conversation(it, "address-$it", latestDate = it) }
        val scope = GmailBackupConversationLimiter.applyConversations(source, null)
        assertEquals((20L downTo 1L).toList(), scope.conversations.map { it.threadId })
        assertFalse(scope.isLimitedTest)
    }

    @Test fun `progress total uses limited candidates`() {
        val scope = GmailBackupConversationLimiter.applyConversations(
            (1L..20L).map { conversation(it, "address-$it", latestDate = it) }, 10
        )
        val progress = GmailBackupWorkProgress(
            GmailBackupPhase.RUNNING, "profile", checked = 5,
            total = scope.conversations.size, statusMessage = "Testing"
        )
        assertEquals(10, progress.total)
        assertEquals(0.5f, progress.fraction)
    }

    @Test fun `limited completion is not a complete full backup`() {
        val completion = GmailBackupCompletion(
            state = GmailBackupCompletionState.LIMITED_TEST_COMPLETED,
            checked = 10, total = 10, uploaded = 10, unchanged = 0, failed = 0,
            totalMessages = 100, isLimitedTest = true,
            sourceConversationTotal = 20, sourceMessageTotal = 200
        )
        assertTrue(completion.isLimitedTest)
        assertTrue(completion.state != GmailBackupCompletionState.COMPLETED)
        assertEquals(20, completion.sourceConversationTotal)
        assertEquals(100, completion.totalMessages)
        assertFalse(
            GmailBackupTestModePolicy.shouldAdvanceFullBackupMetadata(
                isLimitedTest = true,
                aborted = false
            )
        )
    }

    @Test fun `temporary test mode allows archive and mirror`() {
        assertTrue(GmailBackupTestModePolicy.supports(GmailBackupMode.ARCHIVE_APPEND_ONLY))
        assertTrue(GmailBackupTestModePolicy.supports(GmailBackupMode.MIRROR))
    }

    @Test fun `newest local activity is selected first regardless of input order`() {
        val source = listOf(
            conversation(1, "a", latestDate = 100),
            conversation(3, "c", latestDate = 300),
            conversation(2, "b", latestDate = 200)
        )
        val scope = GmailBackupConversationLimiter.applyConversations(source, 10)
        assertEquals(listOf(3L, 2L, 1L), scope.conversations.map { it.threadId })
    }

    @Test fun `only ten newest conversations are selected and older conversation is excluded`() {
        val source = (1L..12L).map { conversation(it, "address-$it", latestDate = it) }
        val scope = GmailBackupConversationLimiter.applyConversations(source, 10)
        assertEquals((12L downTo 3L).toList(), scope.conversations.map { it.threadId })
        assertFalse(scope.conversations.any { it.threadId == 2L })
    }

    @Test fun `equal latest timestamps use descending thread id`() {
        val source = listOf(
            conversation(4, "a", latestDate = 500),
            conversation(9, "b", latestDate = 500)
        )
        assertEquals(
            listOf(9L, 4L),
            GmailBackupConversationLimiter.applyConversations(source, 10)
                .conversations.map { it.threadId }
        )
    }

    @Test fun `deleting latest sms can move conversation behind another conversation`() {
        val beforeDeletion = SmsConversationSnapshot(
            1, "a", null, listOf(message(1, 1, "a", 100), message(2, 1, "a", 300))
        )
        val afterDeletion = beforeDeletion.copy(messages = beforeDeletion.messages.dropLast(1))
        val other = conversation(2, "b", latestDate = 200)
        assertEquals(1L, GmailBackupConversationLimiter.applyConversations(listOf(other, beforeDeletion), 10).conversations.first().threadId)
        assertEquals(2L, GmailBackupConversationLimiter.applyConversations(listOf(other, afterDeletion), 10).conversations.first().threadId)
    }

    @Test fun `archive and mirror receive the same recent scope`() {
        val source = (1L..12L).map { conversation(it, "address-$it", latestDate = it) }
        val archiveScope = GmailBackupConversationLimiter.applyConversations(source, 10).conversations
        val mirrorScope = GmailBackupConversationLimiter.applyConversations(source, 10).conversations
        assertEquals(archiveScope, mirrorScope)
    }

    @Test fun `changed message count changes snapshot hash`() {
        val original = conversation(1, "+919876543210", 1)
        val changed = conversation(1, "+919876543210", 2)
        assertTrue(
            ConversationSnapshotHashGenerator.generate(original) !=
                ConversationSnapshotHashGenerator.generate(changed)
        )
    }

    private fun conversation(
        threadId: Long,
        address: String,
        count: Int = 1,
        latestDate: Long = count.toLong()
    ) =
        SmsConversationSnapshot(
            threadId = threadId,
            address = address,
            contactName = null,
            messages = (1..count).map { index ->
                message(index.toLong(), threadId, address, latestDate - count + index)
            }
        )

    private fun message(id: Long, threadId: Long, address: String, date: Long) =
        SmsMessage(id, threadId, address, null, "body-$id", date, "", 1)
}
