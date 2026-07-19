package io.github.isht1008.opensmsbackup.restore

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RestoreEngineTest {
    @Test fun `existing local V1 or V2 equivalent message is skipped`() = runBlocking {
        val existing = message(1, "09876543210", 100, "hello", 1)
        val source = existing.copy(id = 2, address = "+91 98765 43210")
        var writes = 0
        val result = DefaultRestoreEngine { writes++; Result.success(Unit) }
            .restore(listOf(source), listOf(existing), "IN")
        assertEquals(0, writes); assertEquals(1, result.skippedDuplicates)
    }

    @Test fun `sender ids direction timestamp and body remain conservative`() = runBlocking {
        val existing = listOf(message(1, "HDFCBK", 100, "same", 1))
        val source = listOf(
            message(2, "AX-HDFCBK", 100, "same", 1),
            message(3, "HDFCBK", 100, "same", 2),
            message(4, "HDFCBK", 101, "same", 1),
            message(5, "HDFCBK", 100, "other", 1)
        )
        val inserted = mutableListOf<SmsMessage>()
        val result = DefaultRestoreEngine { inserted += it; Result.success(Unit) }
            .restore(source, existing, "IN")
        assertEquals(4, result.restored); assertEquals(4, inserted.size)
    }

    @Test fun `rerun is duplicate safe`() = runBlocking {
        val source = message(1, "+919876543210", 100, "hello", 1)
        val inserted = mutableListOf<SmsMessage>()
        val engine = DefaultRestoreEngine { inserted += it; Result.success(Unit) }
        assertEquals(1, engine.restore(listOf(source), emptyList(), "IN").restored)
        assertEquals(1, engine.restore(listOf(source), inserted, "IN").skippedDuplicates)
    }

    @Test fun `one failure continues and cancellation returns partial result`() = runBlocking {
        var calls = 0
        val engine = DefaultRestoreEngine { message ->
            calls++
            if (message.id == 2L) Result.failure(IllegalStateException())
            else if (message.id == 3L) throw CancellationException()
            else Result.success(Unit)
        }
        val result = engine.restore(listOf(message(1), message(2), message(3), message(4)), emptyList(), "IN")
        assertEquals(1, result.restored); assertEquals(1, result.failed); assertTrue(result.cancelled)
        assertEquals(3, calls)
    }

    @Test fun `role rejection performs no insertion`() = runBlocking {
        var writes = 0
        val executor = RoleGatedRestoreExecutor({ false }, DefaultRestoreEngine { writes++; Result.success(Unit) })
        assertNull(executor.execute(listOf(message(1)), emptyList(), "IN")); assertEquals(0, writes)
    }

    private fun message(id: Long, address: String = "+919876543210", date: Long = id,
        body: String = "body-$id", type: Int = 1) =
        SmsMessage(id, address = address, contactName = null, body = body, date = date,
            dateFormatted = "", type = type)
}
