package io.github.isht1008.opensmsbackup.gmail.account

import android.content.Context
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.gmail.auth.GmailAuthorizationManager
import io.github.isht1008.opensmsbackup.gmail.auth.GoogleSignInManager
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkCoordinator
import io.github.isht1008.opensmsbackup.verification.BackupVerificationWorkCoordinator

class AndroidGmailAccountExitOperations(context: Context) : GmailAccountExitOperations {
    private val appContext = context.applicationContext
    private val backupCoordinator = GmailBackupWorkCoordinator(appContext)
    private val verificationCoordinator = BackupVerificationWorkCoordinator(appContext)
    private val authorizationManager = GmailAuthorizationManager(appContext)
    private val signInManager = GoogleSignInManager(appContext)
    private val accountManager = GmailAccountManager(appContext)

    override suspend fun hasActiveBackup(profileId: String) = backupCoordinator.hasActiveWork(profileId)
    override suspend fun hasActiveVerification(profileId: String) = verificationCoordinator.hasActiveWork(profileId)
    override suspend fun revokeGoogleAccess(profile: AccountProfileEntity) = authorizationManager.revokeAccess(profile)
    override suspend fun clearCredentialSession() = signInManager.clearCredentialState()
    override suspend fun disconnectLocally(profile: AccountProfileEntity) = accountManager.disconnectAccountProfile(profile)
    override suspend fun markAuthorizationRequired(profile: AccountProfileEntity) = accountManager.markAuthorizationRequired(profile)
}
