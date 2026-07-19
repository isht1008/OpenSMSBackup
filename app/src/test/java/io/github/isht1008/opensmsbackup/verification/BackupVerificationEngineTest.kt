package io.github.isht1008.opensmsbackup.verification

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupVerificationEngineTest {
    @Test fun `mirror and archive append modes use the same fingerprint integrity rules`() = runBlocking {
        GmailBackupMode.entries.forEach { mode ->
            val engine = DefaultBackupVerificationEngine(VerificationArchiveRepository {
                Result.success(VerificationArchiveSnapshot(listOf(message(9, date = 1)), 1))
            })
            assertEquals(BackupVerificationStatus.VERIFIED,
                engine.verify(request(listOf(message(1)), mode)).status)
        }
    }

    @Test fun `exact messages verify`() = runBlocking {
        val result = verify(listOf(message(1)), listOf(message(9, date = 1)))
        assertEquals(BackupVerificationStatus.VERIFIED, result.status)
        assertEquals(1, result.matchedMessageCount)
    }

    @Test fun `country formatting and V1 V2 aliases match`() = runBlocking {
        val local = message(1, "09876543210")
        val remote = local.copy(id = 99, address = "+91 98765 43210")
        assertEquals(BackupVerificationStatus.VERIFIED, verify(listOf(local), listOf(remote)).status)
    }

    @Test fun `missing extra and duplicate records classify safely`() = runBlocking {
        val missing = verify(listOf(message(1)), emptyList(), conversations = 1)
        assertEquals(BackupVerificationStatus.FAILED, missing.status)
        assertEquals(1, missing.missingMessageCount)

        val extra = verify(listOf(message(1)), listOf(message(1), message(2)))
        assertEquals(BackupVerificationStatus.PARTIALLY_VERIFIED, extra.status)
        assertEquals(1, extra.unexpectedArchivedMessageCount)

        val duplicate = verify(listOf(message(1)), listOf(message(1), message(99, date = 1)))
        assertEquals(1, duplicate.duplicateFingerprintCount)
        assertEquals(1, duplicate.unexpectedArchivedMessageCount)
    }

    @Test fun `aliases for one record are not duplicates and one record is consumed once`() = runBlocking {
        assertEquals(0, verify(listOf(message(1)), listOf(message(1))).duplicateFingerprintCount)
        val result = verify(listOf(message(1), message(2, date = 1)), listOf(message(9, date = 1)))
        assertEquals(1, result.matchedMessageCount)
        assertEquals(1, result.missingMessageCount)
    }

    @Test fun `direction timestamp and body differences do not match`() = runBlocking {
        val base = message(1)
        assertEquals(1, verify(listOf(base), listOf(base.copy(type = 2))).missingMessageCount)
        assertEquals(1, verify(listOf(base), listOf(base.copy(date = 2))).missingMessageCount)
        assertEquals(1, verify(listOf(base), listOf(base.copy(body = "other"))).missingMessageCount)
    }

    @Test fun `sender ids never use suffix similarity`() = runBlocking {
        val local = listOf(message(1, "AX-HDFCBK"), message(2, "VM-HDFCBK"), message(3, "HDFCBK"))
        val remote = listOf(
            local[2].copy(id = 4),
            local[0].copy(id = 5),
            local[1].copy(id = 6)
        )
        assertEquals(BackupVerificationStatus.VERIFIED, verify(local, remote).status)
        assertEquals(1, verify(listOf(local[0]), listOf(remote[0])).missingMessageCount)
    }

    @Test fun `unreadable incomplete no archive and cancellation cannot verify`() = runBlocking {
        val unreadable = verify(listOf(message(1)), listOf(message(1)), unreadable = 1)
        assertEquals(BackupVerificationStatus.PARTIALLY_VERIFIED, unreadable.status)
        val incomplete = verify(listOf(message(1)), listOf(message(1)), complete = false)
        assertEquals(BackupVerificationStatus.PARTIALLY_VERIFIED, incomplete.status)
        assertEquals(BackupVerificationStatus.NOT_AVAILABLE, verify(listOf(message(1)), emptyList()).status)
        val cancelledEngine = DefaultBackupVerificationEngine(VerificationArchiveRepository { throw CancellationException() })
        assertEquals(BackupVerificationStatus.CANCELLED, cancelledEngine.verify(request(listOf(message(1)))).status)
    }

    private suspend fun verify(local: List<SmsMessage>, remote: List<SmsMessage>, conversations: Int = if (remote.isEmpty()) 0 else 1,
        unreadable: Int = 0, complete: Boolean = true) = DefaultBackupVerificationEngine(VerificationArchiveRepository {
        Result.success(VerificationArchiveSnapshot(remote, conversations, unreadable, complete = complete))
    }).verify(request(local))

    private fun request(messages: List<SmsMessage>, mode: GmailBackupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY) = BackupVerificationRequest(
        "profile", "user@example.com", "device", "Phone", mode, "IN", messages)
    private fun message(id: Long, address: String = "+919876543210", date: Long = id, body: String = "body", type: Int = 1) =
        SmsMessage(id, 7, address, null, body, date, "", type)
}
