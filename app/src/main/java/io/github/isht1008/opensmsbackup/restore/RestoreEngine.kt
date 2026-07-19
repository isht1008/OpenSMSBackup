package io.github.isht1008.opensmsbackup.restore

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import kotlinx.coroutines.CancellationException

fun interface SmsRestoreWriter { suspend fun insert(message: SmsMessage): Result<Unit> }

interface RestoreEngine {
    suspend fun restore(
        messages: List<SmsMessage>,
        existingMessages: List<SmsMessage>,
        defaultRegion: String,
        onProgress: suspend (RestoreResult) -> Unit = {}
    ): RestoreResult
}

class DefaultRestoreEngine(private val writer: SmsRestoreWriter) : RestoreEngine {
    override suspend fun restore(
        messages: List<SmsMessage>,
        existingMessages: List<SmsMessage>,
        defaultRegion: String,
        onProgress: suspend (RestoreResult) -> Unit
    ): RestoreResult {
        val ordered = messages.sortedWith(compareBy<SmsMessage> { it.date }.thenBy { it.id })
        val duplicates = RestoreDuplicateIndex(existingMessages, defaultRegion)
        var result = RestoreResult(ordered.size, 0, 0, 0, false)
        try {
            for (message in ordered) {
                result = if (duplicates.contains(message)) {
                    result.copy(skippedDuplicates = result.skippedDuplicates + 1)
                } else {
                    writer.insert(message).fold(
                        onSuccess = {
                            duplicates.record(message)
                            result.copy(restored = result.restored + 1)
                        },
                        onFailure = { result.copy(failed = result.failed + 1) }
                    )
                }
                onProgress(result)
            }
            return result
        } catch (cancelled: CancellationException) {
            result = result.copy(cancelled = true)
            return result
        }
    }
}
