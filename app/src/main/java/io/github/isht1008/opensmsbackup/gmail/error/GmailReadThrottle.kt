package io.github.isht1008.opensmsbackup.gmail.error

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GmailReadThrottle(
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val delayBlock: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var blockedUntilMillis = 0L

    suspend fun awaitPermission(): Long {
        var waitedMillis = 0L
        while (true) {
            val waitMillis = mutex.withLock {
                (blockedUntilMillis - nowMillis()).coerceAtLeast(0L)
            }
            if (waitMillis == 0L) return waitedMillis
            waitedMillis += waitMillis
            delayBlock(waitMillis)
        }
    }

    suspend fun defer(delayMillis: Long) {
        mutex.withLock {
            blockedUntilMillis = maxOf(
                blockedUntilMillis,
                nowMillis() + delayMillis.coerceAtLeast(0L)
            )
        }
    }
}
