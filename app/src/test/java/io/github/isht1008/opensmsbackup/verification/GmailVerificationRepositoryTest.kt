package io.github.isht1008.opensmsbackup.verification

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.backup.*
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GmailVerificationRepositoryTest {
    @Test fun `pagination reads every page without mutation`() = runBlocking {
        val gateway = FakeGateway(
            pages = mapOf(null to VerificationMessagePage(listOf("a"), "next"),
                "next" to VerificationMessagePage(listOf("b"), null)),
            documents = mapOf("a" to document("a", "123"), "b" to document("b", "456"))
        )
        val result = GmailVerificationRepository(gateway, "label").loadArchive(request()).getOrThrow()
        assertEquals(2, result.conversationCount)
        assertEquals(listOf(null, "next"), gateway.pageTokens)
        assertEquals(0, gateway.mutations)
    }

    @Test fun `wrong ownership invalid key and unsupported version are rejected`() = runBlocking {
        val values = listOf(
            document("a", "123").copy(accountEmail = "other@example.com"),
            document("b", "456").copy(deviceIdHeader = "other"),
            document("c", "789").copy(conversationKeyHeader = "bad"),
            document("d", "999").copy(identityVersionHeader = "99")
        )
        val gateway = FakeGateway(mapOf(null to VerificationMessagePage(values.map { it.messageId }, null)),
            values.associateBy { it.messageId })
        val result = GmailVerificationRepository(gateway, "label").loadArchive(request()).getOrThrow()
        assertEquals(0, result.conversationCount)
        assertEquals(setOf(BackupVerificationIssueType.WRONG_ACCOUNT, BackupVerificationIssueType.WRONG_DEVICE,
            BackupVerificationIssueType.INVALID_CONVERSATION_IDENTITY, BackupVerificationIssueType.UNSUPPORTED_ARCHIVE_VERSION),
            result.issues.map { it.type }.toSet())
    }

    @Test fun `safety limit marks snapshot incomplete`() = runBlocking {
        val docs = (1..3).associate { "m$it" to document("m$it", "$it") }
        val gateway = FakeGateway(mapOf(null to VerificationMessagePage(docs.keys.toList(), null)), docs)
        val result = GmailVerificationRepository(gateway, "label", maximumMessages = 2).loadArchive(request()).getOrThrow()
        assertFalse(result.complete)
        assertTrue(result.issues.any { it.type == BackupVerificationIssueType.SAFETY_LIMIT_REACHED })
    }

    @Test fun `newest snapshot replaces older snapshot without accumulating history`() = runBlocking {
        val older = document("old", "123").copy(internalDate = 1)
        val newer = document("new", "123").copy(internalDate = 2)
        val gateway = FakeGateway(
            mapOf(null to VerificationMessagePage(listOf("old", "new"), null)),
            mapOf("old" to older, "new" to newer)
        )

        val result = GmailVerificationRepository(gateway, "label").loadArchive(request()).getOrThrow()

        assertEquals(1, result.conversationCount)
        assertEquals(listOf(newer.conversation.messages.single()), result.messages)
    }

    @Test fun `repeated page token terminates as incomplete`() = runBlocking {
        val doc = document("a", "123")
        val gateway = FakeGateway(
            mapOf(
                null to VerificationMessagePage(listOf("a"), "loop"),
                "loop" to VerificationMessagePage(emptyList(), "loop")
            ),
            mapOf("a" to doc)
        )

        val result = GmailVerificationRepository(gateway, "label").loadArchive(request()).getOrThrow()

        assertFalse(result.complete)
        assertTrue(result.issues.any { it.type == BackupVerificationIssueType.SAFETY_LIMIT_REACHED })
        assertEquals(listOf(null, "loop"), gateway.pageTokens)
    }

    private fun request() = BackupVerificationRequest("p", "user@example.com", "device", "Phone",
        GmailBackupMode.MIRROR, "IN", emptyList())
    private fun document(id: String, address: String): GmailArchiveDocument {
        val key = ArchiveConversationIdentity.key(ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
            address, "user@example.com", "device", "IN")
        val sms = SmsMessage(id.hashCode().toLong(), 1, address, null, "body", 1, "", 1)
        return GmailArchiveDocument(id, null, 1, "user@example.com", key,
            SmsConversationSnapshot(1, address, null, listOf(sms)), "device", "3", setOf("label"), "IN")
    }
    private class FakeGateway(
        val pages: Map<String?, VerificationMessagePage>,
        val documents: Map<String, GmailArchiveDocument>
    ) : VerificationGmailGateway {
        val pageTokens = mutableListOf<String?>(); var mutations = 0
        override suspend fun listPage(labelId: String, pageToken: String?) = Result.success(
            pages.getValue(pageToken).also { pageTokens += pageToken })
        override suspend fun read(messageId: String) = documents[messageId]?.let(Result.Companion::success)
            ?: Result.failure(IllegalArgumentException())
    }
}
