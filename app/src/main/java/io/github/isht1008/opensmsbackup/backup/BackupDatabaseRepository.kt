package io.github.isht1008.opensmsbackup.backup

import io.github.isht1008.opensmsbackup.database.BackupDatabase
import io.github.isht1008.opensmsbackup.database.BackupMessageEntity

class BackupDatabaseRepository(
    private val database: BackupDatabase
) {

    suspend fun isAlreadyBackedUp(
        accountEmail: String,
        fingerprint: String
    ): Boolean {

        return database
            .backupMessageDao()
            .findByFingerprint(
                accountEmail,
                fingerprint
            ) != null
    }

    suspend fun save(
        entity: BackupMessageEntity
    ): Long {

        return database
            .backupMessageDao()
            .insert(entity)
    }

    suspend fun updateGmailId(
        id: Long,
        gmailId: String
    ) {

        database
            .backupMessageDao()
            .updateGmailId(
                id,
                gmailId
            )
    }
}