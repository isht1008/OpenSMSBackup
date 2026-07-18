package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.gmail.error.GmailErrorClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailFailureCircuitBreakerTest {
    private val classifier = GmailErrorClassifier()

    @Test fun `fatal failure aborts immediately`() {
        assertTrue(
            GmailFailureCircuitBreaker()
                .recordFailure(classifier.classifyHttp(401))
        )
    }

    @Test fun `five matching failures abort`() {
        val breaker = GmailFailureCircuitBreaker()
        val failure = classifier.classifyHttp(429)
        repeat(4) { assertFalse(breaker.recordFailure(failure)) }
        assertTrue(breaker.recordFailure(failure))
    }

    @Test fun `success resets consecutive failures`() {
        val breaker = GmailFailureCircuitBreaker()
        val failure = classifier.classifyHttp(429)
        repeat(4) { breaker.recordFailure(failure) }
        breaker.recordSuccess()
        assertFalse(breaker.recordFailure(failure))
    }

    @Test fun `unrelated failures do not abort prematurely`() {
        val breaker = GmailFailureCircuitBreaker()
        val rateLimit = classifier.classifyHttp(429)
        val server = classifier.classifyHttp(503)
        repeat(4) {
            assertFalse(breaker.recordFailure(rateLimit))
            assertFalse(breaker.recordFailure(server))
        }
    }
}
