package io.github.isht1008.opensmsbackup.account.data

import android.content.Context
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.AccountSettingsEntity
import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale

data class AccountManagementItem(
    val profile: AccountProfileEntity,
    val policy: GmailBackupMode,
    val lastSuccessfulBackupAt: Long?,
    val verification: BackupVerificationEntity?
)

class AccountManagementRepository private constructor(
    private val context: Context
) {
    private val database = DatabaseProvider.getDatabase(context)
    private val accountDao = database.accountProfileDao()

    fun observeItems(): Flow<List<AccountManagementItem>> =
        combine(
            accountDao.observeProfiles(),
            accountDao.observeSettings(),
            database.backupAccountDao().observeAll(),
            database.backupVerificationDao().observeHistory()
        ) { profiles, settings, backups, verifications ->
            AccountSourceData(profiles, settings, backups, verifications)
        }.map { source ->
            val deviceId = DeviceProfileStore.create(context).getOrCreate().deviceId
            val backupsByEmail = source.backups.associateBy {
                it.accountEmail.trim().lowercase(Locale.ROOT)
            }
            val backupTimes = source.profiles.associate { profile ->
                profile.profileId to backupsByEmail[profile.accountEmail.trim().lowercase(Locale.ROOT)]
                    ?.lastBackupTime
                    ?.takeIf { it > 0L }
            }
            val verifications = source.profiles.associate { profile ->
                profile.profileId to source.verifications.firstOrNull {
                    it.profileId == profile.profileId && it.deviceId == deviceId
                }
            }
            AccountManagementItemFactory.create(source.profiles, source.settings, backupTimes, verifications)
        }

    companion object {
        fun create(context: Context) = AccountManagementRepository(context.applicationContext)
    }
}

private data class AccountSourceData(
    val profiles: List<AccountProfileEntity>,
    val settings: List<AccountSettingsEntity>,
    val backups: List<io.github.isht1008.opensmsbackup.database.BackupAccountEntity>,
    val verifications: List<BackupVerificationEntity>
)

object AccountManagementItemFactory {
    fun create(
        profiles: List<AccountProfileEntity>,
        settings: List<AccountSettingsEntity>,
        backupTimesByProfile: Map<String, Long?> = emptyMap(),
        verificationsByProfile: Map<String, BackupVerificationEntity?> = emptyMap()
    ): List<AccountManagementItem> {
        val settingsByProfile = settings.associateBy(AccountSettingsEntity::profileId)
        return profiles.map { profile ->
            AccountManagementItem(
                profile = profile,
                policy = GmailBackupMode.fromStorage(settingsByProfile[profile.profileId]?.backupMode),
                lastSuccessfulBackupAt = backupTimesByProfile[profile.profileId],
                verification = verificationsByProfile[profile.profileId]
            )
        }
    }
}
