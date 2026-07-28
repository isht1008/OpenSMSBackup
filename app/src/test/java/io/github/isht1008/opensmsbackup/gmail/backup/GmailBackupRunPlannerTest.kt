package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GmailBackupRunPlannerTest {
    @Test fun `full archive plan contains every conversation`() {
        val plan = GmailBackupRunPlanner.create(
            allConversations = conversations(20),
            scope = GmailBackupScope.FULL,
            mode = GmailBackupMode.ARCHIVE_APPEND_ONLY
        )
        assertNull(plan.blockedReason)
        assertEquals((20L downTo 1L).toList(), plan.conversations.map { it.threadId })
    }

    @Test fun `recent test plan contains deterministic recent ten`() {
        val plan = GmailBackupRunPlanner.create(
            allConversations = conversations(20),
            scope = GmailBackupScope.RECENT_TEST,
            mode = GmailBackupMode.ARCHIVE_APPEND_ONLY
        )
        assertEquals((20L downTo 11L).toList(), plan.conversations.map { it.threadId })
    }

    @Test fun `full mirror plan is blocked`() {
        val plan = GmailBackupRunPlanner.create(
            allConversations = conversations(20),
            scope = GmailBackupScope.FULL,
            mode = GmailBackupMode.MIRROR
        )
        assertNotNull(plan.blockedReason)
    }

    private fun conversations(count: Int) =
        (1L..count.toLong()).map { threadId ->
            SmsConversationSnapshot(
                threadId = threadId,
                address = "address-$threadId",
                contactName = null,
                messages = listOf(
                    SmsMessage(
                        id = threadId,
                        threadId = threadId,
                        address = "address-$threadId",
                        contactName = null,
                        body = "fixture",
                        date = threadId,
                        dateFormatted = "",
                        type = 1
                    )
                )
            )
        }
}
