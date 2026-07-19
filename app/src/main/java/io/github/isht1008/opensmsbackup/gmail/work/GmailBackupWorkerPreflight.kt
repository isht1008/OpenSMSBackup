package io.github.isht1008.opensmsbackup.gmail.work

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.error.GmailErrorClassifier

object GmailBackupWorkerPreflight {
    fun failureFor(profile: AccountProfileEntity?): GmailBackupCompletion? {
        if (profile == null) {
            return completion(
                reason = "The Gmail account profile no longer exists."
            )
        }
        if (profile.connectionState == AccountProfileEntity.CONNECTION_STATE_CONNECTED) {
            return null
        }
        val authorizationRequired =
            profile.connectionState == AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED
        return completion(
            reason = if (authorizationRequired) {
                "Gmail authorization is required for ${profile.accountEmail}. Open Settings and re-authorize this account."
            } else {
                "The Gmail account is disconnected."
            },
            profile = profile,
            authorizationRequired = authorizationRequired
        )
    }

    private fun completion(
        reason: String,
        profile: AccountProfileEntity? = null,
        authorizationRequired: Boolean = false
    ) = GmailBackupCompletion(
        state = GmailBackupCompletionState.FAILED_BEFORE_START,
        checked = 0,
        total = 0,
        uploaded = 0,
        unchanged = 0,
        failed = 0,
        reason = reason,
        profileId = profile?.profileId,
        accountEmail = profile?.accountEmail,
        failure = if (authorizationRequired) {
            GmailErrorClassifier().classifyHttp(401)
        } else null
    )
}
