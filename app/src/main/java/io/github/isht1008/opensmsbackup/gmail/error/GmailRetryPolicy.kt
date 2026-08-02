package io.github.isht1008.opensmsbackup.gmail.error

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

class GmailRetryPolicy(
    private val classifier: GmailErrorClassifier = GmailErrorClassifier(),
    private val maximumAttempts: Int = 3,
    private val maximumDelayMillis: Long = 10_000L,
    private val jitterMillis: () -> Long = { Random.nextLong(0L, 501L) },
    private val delayBlock: suspend (Long) -> Unit = { delay(it) },
    private val logger: (String) -> Unit = { Log.w("OpenSMSBackup", it) }
) {
    suspend fun <T> execute(
        operationName: String,
        profileId: String,
        onRetry: suspend (attempt: Int, maximumAttempts: Int) -> Unit = { _, _ -> },
        onBackoff: suspend (Long) -> Unit = {},
        operation: suspend () -> T
    ): T {
        require(maximumAttempts > 0)

        for (attempt in 1..maximumAttempts) {
            try {
                return operation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val failure =
                    if (error is GmailOperationException) error.failure
                    else classifier.classify(error)
                val finalAttempt = attempt == maximumAttempts

                if (!failure.retryable || finalAttempt) {
                    logger(diagnostic(operationName, attempt, failure, null))
                    throw GmailOperationException(failure)
                }

                val exponential = 1_000L shl (attempt - 1)
                val retryDelay = maxOf(
                    minOf(maximumDelayMillis, exponential + jitterMillis()),
                    failure.retryAfterMillis ?: 0L
                )

                logger(diagnostic(operationName, attempt, failure, retryDelay))
                onRetry(attempt + 1, maximumAttempts)
                onBackoff(retryDelay)
                delayBlock(retryDelay)
            }
        }

        error("Retry loop completed unexpectedly.")
    }

    private fun diagnostic(
        operation: String,
        attempt: Int,
        failure: GmailFailure,
        delayMillis: Long?
    ): String =
        "gmail_operation=$operation scope=profile_bound attempt=$attempt " +
            "status=${failure.httpStatusCode} " +
            "category=${failure.category} subtype=${failure.safeExceptionSubtype} retry_delay_ms=$delayMillis"
}
