package io.github.isht1008.opensmsbackup.gmail.backup

import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.MessagePartHeader
import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GmailArchiveHeaderReaderTest {
    @Test fun uniqueOwnershipHeaderIsCaseInsensitive() {
        val part = part(
            "x-opensmsbackup-snapshot-hash" to "hash"
        )
        assertEquals(
            "hash",
            GmailArchiveHeaderReader.uniqueValue(part, OpenSmsHeaders.SNAPSHOT_HASH)
        )
    }

    @Test fun duplicateOwnershipHeaderIsRejectedEvenWhenValuesMatch() {
        val part = part(
            OpenSmsHeaders.ACCOUNT to "mirror@example.test",
            OpenSmsHeaders.ACCOUNT.lowercase() to "mirror@example.test"
        )
        assertNull(GmailArchiveHeaderReader.uniqueValue(part, OpenSmsHeaders.ACCOUNT))
    }

    @Test fun missingOwnershipHeaderIsRejected() {
        assertNull(
            GmailArchiveHeaderReader.uniqueValue(MessagePart(), OpenSmsHeaders.DEVICE_ID)
        )
    }

    private fun part(vararg headers: Pair<String, String>) = MessagePart().setHeaders(
        headers.map { (name, value) ->
            MessagePartHeader().setName(name).setValue(value)
        }
    )
}
