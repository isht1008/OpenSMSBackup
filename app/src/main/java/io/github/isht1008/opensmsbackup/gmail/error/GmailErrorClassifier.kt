package io.github.isht1008.opensmsbackup.gmail.error

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class GmailErrorClassifier {

    fun classify(error: Throwable): GmailFailure {
        if (error is CancellationException) {
            throw error
        }

        val googleError = findCause<GoogleJsonResponseException>(error)
        val status = googleError?.statusCode
        val reason = googleError?.details?.errors?.firstOrNull()?.reason
        val retryAfter = retryAfterMillis(googleError)

        if (
            findCause<UserRecoverableAuthIOException>(error) != null ||
            hasCauseNamed(error, "UserRecoverableAuthException") ||
            status == 401
        ) {
            return failure(
                GmailFailureCategory.AUTHORIZATION,
                error,
                status,
                reason,
                "Gmail authorization is required.",
                retryable = false,
                stopBackup = true,
                reauthorizationRequired = true
            )
        }

        if (status != null) {
            return classifyHttp(
                statusCode = status,
                reason = reason,
                originalException = error,
                retryAfterMillis = retryAfter
            )
        }

        if (
            findCause<SocketTimeoutException>(error) != null ||
            findCause<UnknownHostException>(error) != null ||
            findCause<ConnectException>(error) != null ||
            findCause<SocketException>(error) != null
        ) {
            return failure(
                GmailFailureCategory.NETWORK,
                error,
                status,
                reason,
                "Network connection was lost. Check your internet connection and try again.",
                retryable = true,
                stopBackup = false,
                reauthorizationRequired = false
            )
        }

        val local = error is IllegalArgumentException
        return failure(
            if (local) GmailFailureCategory.LOCAL else GmailFailureCategory.UNKNOWN,
            error,
            status,
            reason,
            if (local) "OpenSMSBackup could not prepare this conversation."
            else "An unexpected Gmail error occurred.",
            retryable = false,
            stopBackup = false,
            reauthorizationRequired = false
        )
    }

    fun classifyHttp(
        statusCode: Int,
        reason: String? = null,
        originalException: Throwable = Exception("Gmail HTTP $statusCode"),
        retryAfterMillis: Long? = null
    ): GmailFailure {
        val normalizedReason = reason?.lowercase()

        if (statusCode == 401 || normalizedReason in AUTHORIZATION_REASONS) {
            return failure(
                GmailFailureCategory.AUTHORIZATION,
                originalException,
                statusCode,
                reason,
                "Gmail authorization is required.",
                false,
                true,
                true
            )
        }

        if (statusCode == 403) {
            val retryable = normalizedReason in RETRYABLE_403_REASONS
            return failure(
                if (retryable) GmailFailureCategory.RATE_LIMIT else GmailFailureCategory.CONFIGURATION,
                originalException,
                statusCode,
                reason,
                if (retryable) RATE_LIMIT_MESSAGE else configurationMessage(normalizedReason),
                retryable,
                !retryable,
                normalizedReason in AUTHORIZATION_403_REASONS,
                retryAfterMillis
            )
        }

        if (statusCode == 429) {
            return failure(
                GmailFailureCategory.RATE_LIMIT,
                originalException,
                statusCode,
                reason,
                RATE_LIMIT_MESSAGE,
                true,
                false,
                false,
                retryAfterMillis
            )
        }

        if (statusCode in RETRYABLE_SERVER_STATUSES || normalizedReason == "backenderror") {
            return failure(
                GmailFailureCategory.SERVER,
                originalException,
                statusCode,
                reason,
                "Gmail is temporarily unavailable. Please try again later.",
                true,
                false,
                false,
                retryAfterMillis
            )
        }

        return failure(
            GmailFailureCategory.CLIENT,
            originalException,
            statusCode,
            reason,
            "Gmail rejected the request. Check the app configuration and try again.",
            false,
            true,
            false
        )
    }

    private fun configurationMessage(reason: String?): String =
        when (reason) {
            "accessnotconfigured", "appnotconfigured", "servicedisabled" ->
                "The Gmail API is not enabled for this app's Google Cloud project."
            "insufficientpermissions" ->
                "Gmail permission is missing. Re-authorize this account in Settings."
            else -> "Gmail access was denied. Check the account and app configuration."
        }

    private fun retryAfterMillis(error: GoogleJsonResponseException?): Long? {
        val raw = error?.headers?.get("Retry-After")?.toString() ?: return null
        return raw.toLongOrNull()?.times(1_000L)
    }

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun hasCauseNamed(error: Throwable, simpleName: String): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current.javaClass.simpleName == simpleName) return true
            current = current.cause
        }
        return false
    }

    private fun failure(
        category: GmailFailureCategory,
        error: Throwable,
        status: Int?,
        reason: String?,
        message: String,
        retryable: Boolean,
        stopBackup: Boolean,
        reauthorizationRequired: Boolean,
        retryAfterMillis: Long? = null
    ) = GmailFailure(
        category,
        error,
        status,
        reason,
        message,
        retryable,
        stopBackup,
        reauthorizationRequired,
        retryAfterMillis
    )

    private companion object {
        val RETRYABLE_SERVER_STATUSES = setOf(500, 502, 503, 504)
        val RETRYABLE_403_REASONS = setOf(
            "ratelimitexceeded",
            "userratelimitexceeded",
            "backenderror"
        )
        val AUTHORIZATION_403_REASONS = setOf("insufficientpermissions")
        val AUTHORIZATION_REASONS = setOf(
            "invalidcredentials",
            "insufficientauthentication",
            "autherror",
            "invalidgrant",
            "required"
        )
        const val RATE_LIMIT_MESSAGE =
            "Google temporarily limited Gmail requests. Please try again later."
    }
}
