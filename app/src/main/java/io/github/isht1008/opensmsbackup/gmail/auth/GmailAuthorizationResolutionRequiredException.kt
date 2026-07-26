package io.github.isht1008.opensmsbackup.gmail.auth

import android.app.PendingIntent

class GmailAuthorizationResolutionRequiredException(
    val profileId: String,
    val resolution: PendingIntent,
    val selectAfterAuthorization: Boolean = false
) : Exception("Gmail authorization requires user interaction")
