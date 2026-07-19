package io.github.isht1008.opensmsbackup.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {

    private const val DATABASE_NAME =
        "open_sms_backup.db"

    private val MIGRATION_1_2 =
        object : Migration(1, 2) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `account_email` TEXT NOT NULL,
                        `android_thread_id` INTEGER NOT NULL,
                        `address` TEXT NOT NULL,
                        `contact_name` TEXT,
                        `message_count` INTEGER NOT NULL,
                        `snapshot_hash` TEXT NOT NULL,
                        `gmail_message_id` TEXT NOT NULL,
                        `gmail_thread_id` TEXT,
                        `first_message_date` INTEGER NOT NULL,
                        `last_message_date` INTEGER NOT NULL,
                        `backup_time` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_conversation_snapshots_account_id_android_thread_id`
                    ON `conversation_snapshots`
                    (`account_id`, `android_thread_id`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_conversation_snapshots_account_email`
                    ON `conversation_snapshots`
                    (`account_email`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_conversation_snapshots_android_thread_id`
                    ON `conversation_snapshots`
                    (`android_thread_id`)
                    """.trimIndent()
                )
            }
        }

    private val MIGRATION_2_3 =
        object : Migration(2, 3) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `account_profiles` (
                        `profile_id` TEXT NOT NULL,
                        `provider_account_id` TEXT,
                        `account_email` TEXT NOT NULL,
                        `display_name` TEXT,
                        `photo_url` TEXT,
                        `connection_state` TEXT NOT NULL,
                        `created_time` INTEGER NOT NULL,
                        `updated_time` INTEGER NOT NULL,
                        PRIMARY KEY(`profile_id`)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_account_profiles_account_email`
                    ON `account_profiles` (`account_email`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_account_profiles_provider_account_id`
                    ON `account_profiles` (`provider_account_id`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `account_settings` (
                        `profile_id` TEXT NOT NULL,
                        `backup_enabled` INTEGER NOT NULL,
                        `gmail_enabled` INTEGER NOT NULL,
                        `drive_enabled` INTEGER NOT NULL,
                        `include_contact_names` INTEGER NOT NULL,
                        `backup_mode` TEXT NOT NULL,
                        `backup_label` TEXT NOT NULL,
                        `scheduled_backup_enabled` INTEGER NOT NULL,
                        `encryption_enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`profile_id`),
                        FOREIGN KEY(`profile_id`)
                            REFERENCES `account_profiles`(`profile_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `account_profiles` (
                        `profile_id`,
                        `provider_account_id`,
                        `account_email`,
                        `display_name`,
                        `photo_url`,
                        `connection_state`,
                        `created_time`,
                        `updated_time`
                    )
                    SELECT
                        'legacy:' || lower(`account_email`),
                        NULL,
                        lower(`account_email`),
                        `display_name`,
                        `photo_url`,
                        'CONNECTED',
                        `created_time`,
                        CASE
                            WHEN `last_sync_time` > 0
                                THEN `last_sync_time`
                            ELSE `created_time`
                        END
                    FROM `backup_accounts`
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `account_settings` (
                        `profile_id`,
                        `backup_enabled`,
                        `gmail_enabled`,
                        `drive_enabled`,
                        `include_contact_names`,
                        `backup_mode`,
                        `backup_label`,
                        `scheduled_backup_enabled`,
                        `encryption_enabled`
                    )
                    SELECT
                        'legacy:' || lower(`account_email`),
                        `backup_enabled`,
                        1,
                        0,
                        1,
                        'ARCHIVE',
                        `backup_label`,
                        0,
                        0
                    FROM `backup_accounts`
                    """.trimIndent()
                )
            }
        }

    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE `account_settings` SET `backup_mode` = 'MIRROR'"
                )
            }
        }

    @Volatile
    private var instance: BackupDatabase? = null

    fun getDatabase(
        context: Context
    ): BackupDatabase {

        return instance
            ?: synchronized(this) {

                instance
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        BackupDatabase::class.java,
                        DATABASE_NAME
                    )
                        .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4
                        )
                        .build()
                        .also { database ->
                            instance = database
                        }
            }
    }
}
