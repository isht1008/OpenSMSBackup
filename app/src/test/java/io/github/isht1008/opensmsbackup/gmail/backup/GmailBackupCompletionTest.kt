package io.github.isht1008.opensmsbackup.gmail.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class GmailBackupCompletionTest {
    @Test fun `unattempted conversations are remaining not failed`() {
        val completion = GmailBackupCompletion(
            state = GmailBackupCompletionState.ABORTED_REPEATED_FAILURES,
            checked = 5,
            total = 4_492,
            uploaded = 0,
            unchanged = 0,
            failed = 5
        )

        assertEquals(4_487, completion.remaining)
        assertEquals(5, completion.failed)
    }
}
