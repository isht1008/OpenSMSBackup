package io.github.isht1008.opensmsbackup.gmail.error

enum class GmailFailureCategory {
    AUTHORIZATION,
    CONFIGURATION,
    RATE_LIMIT,
    NETWORK,
    SERVER,
    CLIENT,
    LOCAL,
    UNKNOWN
}

data class GmailFailure(
    val category: GmailFailureCategory,
    val originalException: Throwable,
    val httpStatusCode: Int? = null,
    val googleReason: String? = null,
    val userMessage: String,
    val retryable: Boolean,
    val stopBackup: Boolean,
    val reauthorizationRequired: Boolean,
    val retryAfterMillis: Long? = null
) {
    val circuitKey: String
        get() =
            "$category:${googleReason ?: originalException.javaClass.name}"
}

class GmailOperationException(
    val failure: GmailFailure
) : Exception(failure.userMessage, failure.originalException)
