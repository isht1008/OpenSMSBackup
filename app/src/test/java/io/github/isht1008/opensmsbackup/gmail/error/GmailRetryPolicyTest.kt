package io.github.isht1008.opensmsbackup.gmail.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException

class GmailRetryPolicyTest {
    @Test fun `succeeds after temporary failure`() = runBlocking {
        var calls = 0
        val policy = policy()
        val value = policy.execute("test", "profile") {
            calls++
            if (calls == 1) throw SocketTimeoutException()
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(2, calls)
    }

    @Test fun `stops after maximum attempts`() = runBlocking {
        var calls = 0
        try {
            policy().execute("test", "profile") {
                calls++
                throw SocketTimeoutException()
            }
            fail("Expected GmailOperationException")
        } catch (_: GmailOperationException) {
            assertEquals(3, calls)
        }
    }

    @Test fun `does not retry fatal failure`() = runBlocking {
        var calls = 0
        val failure = GmailErrorClassifier().classifyHttp(401)
        try {
            policy().execute("test", "profile") {
                calls++
                throw GmailOperationException(failure)
            }
            fail("Expected GmailOperationException")
        } catch (_: GmailOperationException) {
            assertEquals(1, calls)
        }
    }

    @Test fun `retry after longer than local maximum is honored`() = runBlocking {
        val delays = mutableListOf<Long>()
        var calls = 0
        val policy = policy(delayBlock = { delays += it })
        val failure = GmailErrorClassifier().classifyHttp(
            429,
            retryAfterMillis = 30_000L
        )
        policy.execute("test", "profile") {
            calls++
            if (calls == 1) throw GmailOperationException(failure)
            Unit
        }
        assertEquals(listOf(30_000L), delays)
    }

    @Test fun `cancellation interrupts retry delay`() = runBlocking {
        val policy = policy(delayBlock = { throw CancellationException() })
        try {
            policy.execute("test", "profile") { throw SocketTimeoutException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            Unit
        }
    }

    @Test fun `retry delay is operation local and does not leak after success`() = runBlocking {
        val delays = mutableListOf<Long>()
        val policy = policy(delayBlock = { delays += it })
        var firstCalls = 0
        policy.execute("first", "profile") {
            firstCalls++
            if (firstCalls == 1) throw SocketTimeoutException()
            Unit
        }
        policy.execute("second", "profile") { Unit }
        assertEquals(listOf(1_000L), delays)
    }

    @Test fun diagnosticsContainSafeSubtypeAndNoProfileIdentifierOrMessage() = runBlocking {
        val logs = mutableListOf<String>()
        val policy = GmailRetryPolicy(
            maximumAttempts = 1,
            jitterMillis = { 0L },
            delayBlock = {},
            logger = { logs += it }
        )
        try {
            policy.execute("get_archive_snapshot", "private-profile-id") {
                throw SocketTimeoutException("private endpoint detail")
            }
            fail("Expected GmailOperationException")
        } catch (_: GmailOperationException) {
            Unit
        }
        assertEquals(1, logs.size)
        val diagnostic = logs.single()
        assertEquals(true, diagnostic.contains("subtype=SOCKET_TIMEOUT"))
        assertEquals(false, diagnostic.contains("private-profile-id"))
        assertEquals(false, diagnostic.contains("private endpoint detail"))
    }

    private fun policy(
        delayBlock: suspend (Long) -> Unit = {}
    ) = GmailRetryPolicy(
        jitterMillis = { 0L },
        delayBlock = delayBlock,
        logger = {}
    )
}
