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

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `backup_verifications` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `profile_id` TEXT NOT NULL, `account_email` TEXT NOT NULL,
                    `device_id` TEXT NOT NULL, `device_name` TEXT NOT NULL,
                    `mode` TEXT NOT NULL, `started_at` INTEGER NOT NULL,
                    `completed_at` INTEGER NOT NULL, `status` TEXT NOT NULL,
                    `local_message_count` INTEGER NOT NULL,
                    `local_conversation_count` INTEGER NOT NULL,
                    `archived_message_count` INTEGER NOT NULL,
                    `archived_conversation_count` INTEGER NOT NULL,
                    `matched_message_count` INTEGER NOT NULL,
                    `missing_message_count` INTEGER NOT NULL,
                    `unexpected_archived_message_count` INTEGER NOT NULL,
                    `duplicate_fingerprint_count` INTEGER NOT NULL,
                    `unreadable_archive_count` INTEGER NOT NULL,
                    `verification_percent` REAL NOT NULL,
                    `short_summary` TEXT NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_verifications_profile_id` ON `backup_verifications` (`profile_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_verifications_completed_at` ON `backup_verifications` (`completed_at`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `account_settings` ADD COLUMN `previous_policy` TEXT DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE `account_settings` ADD COLUMN `policy_changed_at` INTEGER DEFAULT NULL"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `conversation_snapshots` ADD COLUMN `local_source_hash` TEXT DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE `conversation_snapshots` ADD COLUMN `local_source_message_count` INTEGER DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE `conversation_snapshots` ADD COLUMN `local_source_last_message_date` INTEGER DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE `conversation_snapshots` ADD COLUMN `local_source_max_sms_id` INTEGER DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE `conversation_snapshots` ADD COLUMN `local_source_device_id` TEXT DEFAULT NULL"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `conversation_snapshots` ADD COLUMN `profile_id` TEXT NOT NULL DEFAULT ''")
            database.execSQL("UPDATE `conversation_snapshots` SET `profile_id` = COALESCE((SELECT `profile_id` FROM `account_profiles` WHERE lower(`account_profiles`.`account_email`) = lower(`conversation_snapshots`.`account_email`) LIMIT 1), '')")
            database.execSQL("DROP INDEX IF EXISTS `index_conversation_snapshots_account_id_android_thread_id`")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversation_snapshots_profile_id_account_id_android_thread_id` ON `conversation_snapshots` (`profile_id`, `account_id`, `android_thread_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_snapshots_profile_id_local_source_device_id_android_thread_id` ON `conversation_snapshots` (`profile_id`, `local_source_device_id`, `android_thread_id`)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `mirror_reconciliation_runs` (`run_id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `account_identity` TEXT NOT NULL, `device_id` TEXT NOT NULL, `device_label_id` TEXT NOT NULL, `expected_policy` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `expires_at` INTEGER NOT NULL, `local_dataset_fingerprint` TEXT NOT NULL, `remote_index_fingerprint` TEXT NOT NULL, `local_scan_complete` INTEGER NOT NULL, `local_conversations` INTEGER NOT NULL, `owned_remote_conversations` INTEGER NOT NULL, `unchanged_count` INTEGER NOT NULL, `upload_new_count` INTEGER NOT NULL, `replace_changed_count` INTEGER NOT NULL, `trash_remote_only_count` INTEGER NOT NULL, `recover_cache_count` INTEGER NOT NULL, `conflict_count` INTEGER NOT NULL, `foreign_ignored_count` INTEGER NOT NULL, `failed_count` INTEGER NOT NULL, `estimated_reads` INTEGER NOT NULL, `estimated_uploads` INTEGER NOT NULL, `estimated_trash_moves` INTEGER NOT NULL, `estimated_duration_millis` INTEGER NOT NULL, `status` TEXT NOT NULL, `confirmed_at` INTEGER, `completed_at` INTEGER, `terminal_reason` TEXT, PRIMARY KEY(`run_id`), FOREIGN KEY(`profile_id`) REFERENCES `account_profiles`(`profile_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_runs_profile_id` ON `mirror_reconciliation_runs` (`profile_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_runs_profile_id_device_id_status` ON `mirror_reconciliation_runs` (`profile_id`, `device_id`, `status`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_runs_created_at` ON `mirror_reconciliation_runs` (`created_at`)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `mirror_reconciliation_items` (`run_id` TEXT NOT NULL, `item_id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `account_identity` TEXT NOT NULL, `device_id` TEXT NOT NULL, `conversation_key` TEXT NOT NULL, `android_thread_id` INTEGER, `action` TEXT NOT NULL, `expected_local_source_hash` TEXT, `expected_remote_snapshot_hash` TEXT, `prior_gmail_message_id` TEXT, `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `resulting_gmail_message_id` TEXT, `warning_category` TEXT, `failure_category` TEXT, `completed_at` INTEGER, PRIMARY KEY(`run_id`, `item_id`), FOREIGN KEY(`run_id`) REFERENCES `mirror_reconciliation_runs`(`run_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_items_run_id` ON `mirror_reconciliation_items` (`run_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_items_run_id_action_state` ON `mirror_reconciliation_items` (`run_id`, `action`, `state`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_items_profile_id_device_id` ON `mirror_reconciliation_items` (`profile_id`, `device_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_reconciliation_items_profile_id_android_thread_id` ON `mirror_reconciliation_items` (`profile_id`, `android_thread_id`)")
        }
    }
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_profile_id TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_account_identity TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_device_id TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_device_label_id TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_android_thread_id INTEGER")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_gmail_message_id TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_snapshot_hash TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_conversation_key_header TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_identity_version_header TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_format_version_header TEXT")
            database.execSQL("ALTER TABLE mirror_reconciliation_items ADD COLUMN old_target_proof_version TEXT")
            database.execSQL("""
                UPDATE mirror_reconciliation_items
                SET old_target_profile_id = profile_id,
                    old_target_account_identity = account_identity,
                    old_target_device_id = device_id,
                    old_target_device_label_id = (SELECT device_label_id FROM mirror_reconciliation_runs r WHERE r.run_id = mirror_reconciliation_items.run_id),
                    old_target_android_thread_id = android_thread_id,
                    old_target_gmail_message_id = prior_gmail_message_id,
                    old_target_snapshot_hash = expected_remote_snapshot_hash,
                    old_target_proof_version = 'V8_EXACT_ID_HASH_BINDING'
                WHERE prior_gmail_message_id IS NOT NULL
                  AND expected_remote_snapshot_hash IS NOT NULL
                  AND android_thread_id IS NOT NULL
            """.trimIndent())
        }
    }
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `mirror_preview_scans` (`scan_id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `account_fingerprint` TEXT NOT NULL, `device_id` TEXT NOT NULL, `device_label_id` TEXT NOT NULL, `expected_policy` TEXT NOT NULL, `include_contact_names` INTEGER NOT NULL, `lifecycle_state` TEXT NOT NULL, `stage` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `expires_at` INTEGER NOT NULL, `local_dataset_fingerprint` TEXT, `local_scan_complete` INTEGER NOT NULL, `local_count_consistent` INTEGER NOT NULL, `local_failure_category` TEXT, `local_message_count` INTEGER NOT NULL, `local_conversation_count` INTEGER NOT NULL, `local_processed` INTEGER NOT NULL, `remote_generation` INTEGER NOT NULL, `remote_page_cursor` TEXT, `remote_discovery_complete` INTEGER NOT NULL, `remote_discovery_fingerprint` TEXT, `remote_discovered` INTEGER NOT NULL, `metadata_checked` INTEGER NOT NULL, `full_reads_required` INTEGER NOT NULL, `full_reads_completed` INTEGER NOT NULL, `cached_unchanged` INTEGER NOT NULL, `foreign_ignored` INTEGER NOT NULL, `retry_count` INTEGER NOT NULL, `retry_attempt` INTEGER NOT NULL, `retry_at` INTEGER, `last_error_category` TEXT, `last_error_subtype` TEXT, `published_run_id` TEXT, PRIMARY KEY(`scan_id`), FOREIGN KEY(`profile_id`) REFERENCES `account_profiles`(`profile_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_scans_profile_id` ON `mirror_preview_scans` (`profile_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_scans_profile_id_device_id_lifecycle_state` ON `mirror_preview_scans` (`profile_id`, `device_id`, `lifecycle_state`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_scans_expires_at` ON `mirror_preview_scans` (`expires_at`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_scans_published_run_id` ON `mirror_preview_scans` (`published_run_id`)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `mirror_preview_local_items` (`scan_id` TEXT NOT NULL, `item_ordinal` INTEGER NOT NULL, `android_thread_id` INTEGER NOT NULL, `conversation_key` TEXT NOT NULL, `snapshot_hash` TEXT NOT NULL, `local_source_hash` TEXT NOT NULL, `message_count` INTEGER NOT NULL, `diagnostic_reason` TEXT, PRIMARY KEY(`scan_id`, `item_ordinal`), FOREIGN KEY(`scan_id`) REFERENCES `mirror_preview_scans`(`scan_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_local_items_scan_id` ON `mirror_preview_local_items` (`scan_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_local_items_scan_id_conversation_key` ON `mirror_preview_local_items` (`scan_id`, `conversation_key`)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `mirror_preview_remote_items` (`scan_id` TEXT NOT NULL, `gmail_message_id` TEXT NOT NULL, `seen_generation` INTEGER NOT NULL, `item_state` TEXT NOT NULL, `conversation_key` TEXT NOT NULL, `android_thread_id` INTEGER, `snapshot_hash` TEXT, `identity_version` TEXT, `format_version` TEXT, `account_binding_valid` INTEGER NOT NULL, `device_binding_valid` INTEGER NOT NULL, `label_binding_valid` INTEGER NOT NULL, `ownership_valid` INTEGER NOT NULL, `readable` INTEGER NOT NULL, `cache_matches` INTEGER NOT NULL, `identity_current` INTEGER NOT NULL, `reason` TEXT, `full_read_attempts` INTEGER NOT NULL, PRIMARY KEY(`scan_id`, `gmail_message_id`), FOREIGN KEY(`scan_id`) REFERENCES `mirror_preview_scans`(`scan_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_remote_items_scan_id` ON `mirror_preview_remote_items` (`scan_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_remote_items_scan_id_conversation_key` ON `mirror_preview_remote_items` (`scan_id`, `conversation_key`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_remote_items_scan_id_item_state` ON `mirror_preview_remote_items` (`scan_id`, `item_state`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_mirror_preview_remote_items_scan_id_seen_generation` ON `mirror_preview_remote_items` (`scan_id`, `seen_generation`)")
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
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10
                        )
                        .build()
                        .also { database ->
                            instance = database
                        }
            }
    }
}
