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
                    logger(diagnostic(operationName, profileId, attempt, failure, null))
                    throw GmailOperationException(failure)
                }

                val exponential = 1_000L shl (attempt - 1)
                val retryDelay = minOf(
                    maximumDelayMillis,
                    maxOf(exponential + jitterMillis(), failure.retryAfterMillis ?: 0L)
                )

                logger(diagnostic(operationName, profileId, attempt, failure, retryDelay))
                onRetry(attempt + 1, maximumAttempts)
                delayBlock(retryDelay)
            }
        }

        error("Retry loop completed unexpectedly.")
    }

    private fun diagnostic(
        operation: String,
        profileId: String,
        attempt: Int,
        failure: GmailFailure,
        delayMillis: Long?
    ): String =
        "gmail_operation=$operation profile=${profileId.take(8)} attempt=$attempt " +
            "status=${failure.httpStatusCode} reason=${failure.googleReason} " +
            "category=${failure.category} retry_delay_ms=$delayMillis"
}
