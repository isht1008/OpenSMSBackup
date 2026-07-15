package io.github.isht1008.opensmsbackup.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BackupAccountEntity::class,
        BackupMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BackupDatabase : RoomDatabase() {

    abstract fun backupAccountDao(): BackupAccountDao

    abstract fun backupMessageDao(): BackupMessageDao
}