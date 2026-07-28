package io.github.isht1008.opensmsbackup.gmail.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LazyGmailArchiveIndex(
    private val build: suspend () -> GmailArchiveIndex
) {
    private val mutex = Mutex()
    private var value: GmailArchiveIndex? = null

    suspend fun get(): GmailArchiveIndex =
        value ?: mutex.withLock {
            value ?: build().also { value = it }
        }

    val wasBuilt: Boolean
        get() = value != null

    fun peek(): GmailArchiveIndex? = value
}
