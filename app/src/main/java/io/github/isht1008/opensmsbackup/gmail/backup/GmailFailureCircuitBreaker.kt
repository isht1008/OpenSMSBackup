package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.gmail.error.GmailFailure

class GmailFailureCircuitBreaker(
    private val threshold: Int = 5
) {
    private var lastKey: String? = null
    private var consecutiveFailures = 0

    fun recordFailure(failure: GmailFailure): Boolean {
        if (failure.stopBackup) return true

        if (lastKey == failure.circuitKey) {
            consecutiveFailures++
        } else {
            lastKey = failure.circuitKey
            consecutiveFailures = 1
        }

        return consecutiveFailures >= threshold
    }

    fun recordSuccess() {
        lastKey = null
        consecutiveFailures = 0
    }
}
