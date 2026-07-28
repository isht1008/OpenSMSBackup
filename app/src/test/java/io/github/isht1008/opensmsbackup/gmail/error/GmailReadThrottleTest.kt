package io.github.isht1008.opensmsbackup.gmail.error

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailReadThrottleTest {
    @Test fun `longest shared backoff pauses all readers`() = runBlocking {
        var now = 1_000L
        val delays = mutableListOf<Long>()
        val throttle = GmailReadThrottle(
            nowMillis = { now },
            delayBlock = { delay -> delays += delay; now += delay }
        )
        throttle.defer(2_000L)
        throttle.defer(5_000L)
        throttle.awaitPermission()
        assertEquals(listOf(5_000L), delays)
        assertEquals(0L, throttle.awaitPermission())
        assertEquals(listOf(5_000L), delays)
    }

    @Test fun `successful reads have no artificial throttle delay`() = runBlocking {
        val delays = mutableListOf<Long>()
        val throttle = GmailReadThrottle(
            nowMillis = { 1_000L },
            delayBlock = { delays += it }
        )
        assertEquals(0L, throttle.awaitPermission())
        assertEquals(0L, throttle.awaitPermission())
        assertEquals(emptyList<Long>(), delays)
    }
}
