package io.github.isht1008.opensmsbackup.gmail.error

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
        val failure = classifier.classify(SocketTimeoutException())
        assertTrue(failure.retryable)
        assertEquals(GmailSafeExceptionSubtype.SOCKET_TIMEOUT, failure.safeExceptionSubtype)
    }

    @Test fun privacySafeTransportSubtypesRemainDistinct() {
        assertEquals(GmailSafeExceptionSubtype.DNS, classifier.classify(UnknownHostException()).safeExceptionSubtype)
        assertEquals(GmailSafeExceptionSubtype.CONNECTION, classifier.classify(ConnectException()).safeExceptionSubtype)
        assertEquals(GmailSafeExceptionSubtype.SOCKET_RESET, classifier.classify(SocketException("Connection reset")).safeExceptionSubtype)
        assertEquals(GmailSafeExceptionSubtype.SOCKET_OTHER, classifier.classify(SocketException("broken pipe")).safeExceptionSubtype)
        assertEquals(GmailSafeExceptionSubtype.TLS, classifier.classify(SSLException("handshake")).safeExceptionSubtype)
        assertEquals(GmailSafeExceptionSubtype.AUTHORIZATION, classifier.classifyHttp(401).safeExceptionSubtype)
        assertEquals(GmailSafeExceptionSubtype.HTTP, classifier.classifyHttp(503).safeExceptionSubtype)
    }

    @Test fun retryAfterSupportsDeltaSecondsHttpDatesAndOverflow() {
        assertEquals(120_000L, GmailRetryAfterParser.parseMillis("120", 1_000L))
        val now = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val later = now.plusMinutes(30).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        assertEquals(
            30L * 60L * 1_000L,
            GmailRetryAfterParser.parseMillis(later, now.toInstant().toEpochMilli())
        )
        assertEquals(Long.MAX_VALUE, GmailRetryAfterParser.parseMillis(Long.MAX_VALUE.toString(), 0L))
        assertEquals(null, GmailRetryAfterParser.parseMillis("-1", 0L))
    }

    @Test fun `temporary failure does not require profile state change`() {
        assertFalse(classifier.classifyHttp(503).reauthorizationRequired)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation bypasses classification`() {
        classifier.classify(CancellationException())
    }
}
