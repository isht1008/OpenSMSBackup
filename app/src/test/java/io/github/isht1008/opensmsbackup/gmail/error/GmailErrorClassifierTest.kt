package io.github.isht1008.opensmsbackup.gmail.error

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class GmailErrorClassifierTest {
    private val classifier = GmailErrorClassifier()

    @Test fun `401 is fatal and requires authorization`() {
        val failure = classifier.classifyHttp(401)
        assertTrue(failure.stopBackup)
        assertTrue(failure.reauthorizationRequired)
        assertFalse(failure.retryable)
    }

    @Test fun `403 insufficient permissions is fatal`() {
        val failure = classifier.classifyHttp(403, "insufficientPermissions")
        assertTrue(failure.stopBackup)
        assertTrue(failure.reauthorizationRequired)
    }

    @Test fun `403 rate limit is retryable`() {
        assertTrue(classifier.classifyHttp(403, "rateLimitExceeded").retryable)
    }

    @Test fun `429 and server failures are retryable`() {
        assertTrue(classifier.classifyHttp(429).retryable)
        assertTrue(classifier.classifyHttp(500).retryable)
        assertTrue(classifier.classifyHttp(503).retryable)
    }

    @Test fun `malformed request is not retryable`() {
        assertFalse(classifier.classifyHttp(400, "invalidArgument").retryable)
    }

    @Test fun `invalid credentials require authorization`() {
        assertTrue(
            classifier.classifyHttp(400, "invalidCredentials")
                .reauthorizationRequired
        )
    }

    @Test fun `socket timeout is retryable`() {
        assertTrue(classifier.classify(SocketTimeoutException()).retryable)
    }

    @Test fun `temporary failure does not require profile state change`() {
        assertFalse(classifier.classifyHttp(503).reauthorizationRequired)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation bypasses classification`() {
        classifier.classify(CancellationException())
    }
}
