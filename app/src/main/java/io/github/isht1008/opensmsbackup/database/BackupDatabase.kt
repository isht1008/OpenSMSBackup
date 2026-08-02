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
        BackupVerificationEntity::class,
        MirrorReconciliationRunEntity::class,
        MirrorReconciliationItemEntity::class,
        MirrorPreviewScanEntity::class,
        MirrorPreviewLocalItemEntity::class,
        MirrorPreviewRemoteItemEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class BackupDatabase : RoomDatabase() {
    abstract fun backupAccountDao(): BackupAccountDao
    abstract fun backupMessageDao(): BackupMessageDao
    abstract fun conversationSnapshotDao(): ConversationSnapshotDao
    abstract fun accountProfileDao(): AccountProfileDao
    abstract fun backupVerificationDao(): BackupVerificationDao
    abstract fun mirrorReconciliationDao(): MirrorReconciliationDao
    abstract fun mirrorPreviewScanDao(): MirrorPreviewScanDao
}
