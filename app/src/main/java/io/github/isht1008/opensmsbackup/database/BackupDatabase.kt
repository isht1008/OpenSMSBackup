package io.github.isht1008.opensmsbackup.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BackupAccountEntity::class,
        BackupMessageEntity::class,
        ConversationSnapshotEntity::class,
        AccountProfileEntity::class,
        AccountSettingsEntity::class,
        BackupVerificationEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class BackupDatabase : RoomDatabase() {

    abstract fun backupAccountDao(): BackupAccountDao

    abstract fun backupMessageDao(): BackupMessageDao

    abstract fun conversationSnapshotDao(): ConversationSnapshotDao

    abstract fun accountProfileDao(): AccountProfileDao
    abstract fun backupVerificationDao(): BackupVerificationDao
}
