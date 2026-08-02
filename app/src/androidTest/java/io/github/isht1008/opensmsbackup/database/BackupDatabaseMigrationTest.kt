package io.github.isht1008.opensmsbackup.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BackupDatabase::class.java
    )

    @Test fun migration6To7PreservesSnapshotAndInitializesLocalCheckpointAsNull() {
        val name = "migration-6-7"
        helper.createDatabase(name, 6).apply {
            execSQL(
                """
                INSERT INTO conversation_snapshots (
                    account_id, account_email, android_thread_id, address,
                    contact_name, message_count, snapshot_hash, gmail_message_id,
                    gmail_thread_id, first_message_date, last_message_date, backup_time
                ) VALUES (
                    'account-a', 'user@example.com', 7, 'address', NULL, 2,
                    'merged-hash', 'gmail-id', 'gmail-thread', 100, 200, 300
                )
                """
            )
            close()
        }

        helper.runMigrationsAndValidate(
            name,
            7,
            true,
            DatabaseProvider.MIGRATION_6_7
        ).use { database ->
            database.query(
                """
                SELECT snapshot_hash, gmail_message_id, local_source_hash,
                    local_source_message_count, local_source_last_message_date,
                    local_source_max_sms_id, local_source_device_id
                FROM conversation_snapshots
                WHERE account_id = 'account-a' AND android_thread_id = 7
                """
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("merged-hash", cursor.getString(0))
                assertEquals("gmail-id", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals(true, cursor.isNull(4))
                assertEquals(true, cursor.isNull(5))
                assertEquals(true, cursor.isNull(6))
            }
        }
    }

    @Test fun migration3To4PreservesProfilesAndDefaultsModeToMirror() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO account_profiles (
                    profile_id, provider_account_id, account_email, display_name,
                    photo_url, connection_state, created_time, updated_time
                ) VALUES ('profile-a', NULL, 'user@example.com', NULL, NULL,
                    'CONNECTED', 1, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO account_settings (
                    profile_id, backup_enabled, gmail_enabled, drive_enabled,
                    include_contact_names, backup_mode, backup_label,
                    scheduled_backup_enabled, encryption_enabled
                ) VALUES ('profile-a', 1, 1, 0, 1, 'ARCHIVE', 'SMS', 0, 0)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            4,
            true,
            DatabaseProvider.MIGRATION_3_4
        ).use { database ->
            database.query(
                "SELECT backup_mode FROM account_settings WHERE profile_id = 'profile-a'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("MIRROR", cursor.getString(0))
            }
        }
    }

    @Test fun migration4To5PreservesProfilesAndAddsEmptyVerificationHistory() {
        val name = "migration-4-5"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO account_profiles (profile_id, provider_account_id, account_email, display_name, photo_url, connection_state, created_time, updated_time) VALUES ('profile-a', NULL, 'user@example.com', NULL, NULL, 'CONNECTED', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, DatabaseProvider.MIGRATION_4_5).use { database ->
            database.query("SELECT count(*) FROM account_profiles WHERE profile_id = 'profile-a'").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
            database.query("SELECT count(*) FROM backup_verifications").use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun migration5To6PreservesCurrentPolicyAndAddsEmptyPolicyHistoryMetadata() {
        val name = "migration-5-6"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO account_profiles (profile_id, provider_account_id, account_email, display_name, photo_url, connection_state, created_time, updated_time) VALUES ('profile-a', NULL, 'user@example.com', NULL, NULL, 'CONNECTED', 1, 1)")
            execSQL("INSERT INTO account_settings (profile_id, backup_enabled, gmail_enabled, drive_enabled, include_contact_names, backup_mode, backup_label, scheduled_backup_enabled, encryption_enabled) VALUES ('profile-a', 1, 1, 0, 1, 'MIRROR', 'SMS', 0, 0)")
            close()
        }

        helper.runMigrationsAndValidate(
            name,
            6,
            true,
            DatabaseProvider.MIGRATION_5_6
        ).use { database ->
            database.query(
                "SELECT backup_mode, previous_policy, policy_changed_at FROM account_settings WHERE profile_id = 'profile-a'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("MIRROR", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
            }
        }
    }


    @Test fun migration7To8PreservesRowsAndAddsProfileBoundMirrorJournals() {
        val name = "migration-7-8"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO account_profiles (profile_id, provider_account_id, account_email, display_name, photo_url, connection_state, created_time, updated_time) VALUES ('profile-a', NULL, 'archive@example.com', NULL, NULL, 'CONNECTED', 1, 1)")
            execSQL("INSERT INTO account_settings (profile_id, backup_enabled, gmail_enabled, drive_enabled, include_contact_names, backup_mode, previous_policy, policy_changed_at, backup_label, scheduled_backup_enabled, encryption_enabled) VALUES ('profile-a', 1, 1, 0, 1, 'ARCHIVE_APPEND_ONLY', NULL, NULL, 'SMS', 0, 0)")
            execSQL("INSERT INTO conversation_snapshots (account_id, account_email, android_thread_id, address, contact_name, message_count, snapshot_hash, local_source_hash, local_source_message_count, local_source_last_message_date, local_source_max_sms_id, local_source_device_id, gmail_message_id, gmail_thread_id, first_message_date, last_message_date, backup_time) VALUES ('archive@example.com', 'archive@example.com', 7, 'fixture', NULL, 1, 'snapshot', 'local', 1, 2, 3, 'device', 'message', NULL, 1, 2, 3)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, DatabaseProvider.MIGRATION_7_8).use { database ->
            database.query("SELECT profile_id, snapshot_hash, local_source_hash, gmail_message_id FROM conversation_snapshots WHERE android_thread_id = 7").use {
                it.moveToFirst()
                assertEquals("profile-a", it.getString(0))
                assertEquals("snapshot", it.getString(1))
                assertEquals("local", it.getString(2))
                assertEquals("message", it.getString(3))
            }
            database.query("SELECT count(*) FROM mirror_reconciliation_runs").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            database.query("SELECT count(*) FROM mirror_reconciliation_items").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun migration8To9PreservesWarningJournalAndBackfillsImmutableOldTargetProof() {
        val name = "migration-8-9"
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO account_profiles (profile_id, provider_account_id, account_email, display_name, photo_url, connection_state, created_time, updated_time) VALUES ('profile-b', NULL, 'b@example.test', NULL, NULL, 'CONNECTED', 1, 1)")
            execSQL("INSERT INTO mirror_reconciliation_runs (run_id, profile_id, account_identity, device_id, device_label_id, expected_policy, created_at, expires_at, local_dataset_fingerprint, remote_index_fingerprint, local_scan_complete, local_conversations, owned_remote_conversations, unchanged_count, upload_new_count, replace_changed_count, trash_remote_only_count, recover_cache_count, conflict_count, foreign_ignored_count, failed_count, estimated_reads, estimated_uploads, estimated_trash_moves, estimated_duration_millis, status, confirmed_at, completed_at, terminal_reason) VALUES ('run', 'profile-b', 'b@example.test', 'device-b', 'label-b', 'MIRROR', 1, 2, 'local', 'remote', 1, 3332, 21, 9, 3311, 12, 0, 0, 0, 0, 0, 21, 3323, 12, 1, 'COMPLETED_WITH_WARNINGS', 1, 2, NULL)")
            execSQL("INSERT INTO mirror_reconciliation_items (run_id, item_id, profile_id, account_identity, device_id, conversation_key, android_thread_id, action, expected_local_source_hash, expected_remote_snapshot_hash, prior_gmail_message_id, state, attempts, resulting_gmail_message_id, warning_category, failure_category, completed_at) VALUES ('run', 'item', 'profile-b', 'b@example.test', 'device-b', 'key', 7, 'REPLACE_CHANGED', 'local', 'old-hash', 'old-id', 'WARNING', 0, 'new-id', 'TRASH', NULL, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 9, true, DatabaseProvider.MIGRATION_8_9).use { database ->
            database.query("SELECT state, resulting_gmail_message_id, old_target_profile_id, old_target_account_identity, old_target_device_id, old_target_device_label_id, old_target_android_thread_id, old_target_gmail_message_id, old_target_snapshot_hash, old_target_proof_version FROM mirror_reconciliation_items WHERE run_id = 'run'").use {
                it.moveToFirst()
                assertEquals("WARNING", it.getString(0))
                assertEquals("new-id", it.getString(1))
                assertEquals("profile-b", it.getString(2))
                assertEquals("b@example.test", it.getString(3))
                assertEquals("device-b", it.getString(4))
                assertEquals("label-b", it.getString(5))
                assertEquals(7L, it.getLong(6))
                assertEquals("old-id", it.getString(7))
                assertEquals("old-hash", it.getString(8))
                assertEquals("V8_EXACT_ID_HASH_BINDING", it.getString(9))
            }
        }
    }

    @Test fun migration9To10PreservesAccountsCacheAndCompletedMirrorJournal() {
        val name = "migration-9-10"
        helper.createDatabase(name, 9).apply {
            execSQL("INSERT INTO account_profiles (profile_id, provider_account_id, account_email, display_name, photo_url, connection_state, created_time, updated_time) VALUES ('profile-a', NULL, 'archive@example.test', NULL, NULL, 'CONNECTED', 1, 1)")
            execSQL("INSERT INTO account_profiles (profile_id, provider_account_id, account_email, display_name, photo_url, connection_state, created_time, updated_time) VALUES ('profile-b', NULL, 'mirror@example.test', NULL, NULL, 'CONNECTED', 1, 1)")
            execSQL("INSERT INTO account_settings (profile_id, backup_enabled, gmail_enabled, drive_enabled, include_contact_names, backup_mode, previous_policy, policy_changed_at, backup_label, scheduled_backup_enabled, encryption_enabled) VALUES ('profile-a', 1, 1, 0, 1, 'ARCHIVE_APPEND_ONLY', NULL, NULL, 'SMS', 0, 0)")
            execSQL("INSERT INTO account_settings (profile_id, backup_enabled, gmail_enabled, drive_enabled, include_contact_names, backup_mode, previous_policy, policy_changed_at, backup_label, scheduled_backup_enabled, encryption_enabled) VALUES ('profile-b', 1, 1, 0, 1, 'MIRROR', NULL, NULL, 'SMS', 0, 0)")
            execSQL("INSERT INTO conversation_snapshots (profile_id, account_id, account_email, android_thread_id, address, contact_name, message_count, snapshot_hash, local_source_hash, local_source_message_count, local_source_last_message_date, local_source_max_sms_id, local_source_device_id, gmail_message_id, gmail_thread_id, first_message_date, last_message_date, backup_time) VALUES ('profile-b', 'mirror@example.test', 'mirror@example.test', 7, 'fixture', NULL, 1, 'snapshot', 'local', 1, 2, 3, 'device-b', 'message', NULL, 1, 2, 3)")
            execSQL("INSERT INTO mirror_reconciliation_runs (run_id, profile_id, account_identity, device_id, device_label_id, expected_policy, created_at, expires_at, local_dataset_fingerprint, remote_index_fingerprint, local_scan_complete, local_conversations, owned_remote_conversations, unchanged_count, upload_new_count, replace_changed_count, trash_remote_only_count, recover_cache_count, conflict_count, foreign_ignored_count, failed_count, estimated_reads, estimated_uploads, estimated_trash_moves, estimated_duration_millis, status, confirmed_at, completed_at, terminal_reason) VALUES ('completed', 'profile-b', 'mirror@example.test', 'device-b', 'label-b', 'MIRROR', 1, 2, 'local', 'remote', 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 'COMPLETED', 1, 2, NULL)")
            execSQL("INSERT INTO mirror_reconciliation_items (run_id, item_id, profile_id, account_identity, device_id, conversation_key, android_thread_id, action, expected_local_source_hash, expected_remote_snapshot_hash, prior_gmail_message_id, state, attempts, resulting_gmail_message_id, warning_category, failure_category, completed_at, old_target_profile_id, old_target_account_identity, old_target_device_id, old_target_device_label_id, old_target_android_thread_id, old_target_gmail_message_id, old_target_snapshot_hash, old_target_conversation_key_header, old_target_identity_version_header, old_target_format_version_header, old_target_proof_version) VALUES ('completed', 'item', 'profile-b', 'mirror@example.test', 'device-b', 'key', 7, 'UNCHANGED', 'local', 'snapshot', 'message', 'COMPLETED', 0, 'message', NULL, NULL, 2, 'profile-b', 'mirror@example.test', 'device-b', 'label-b', 7, 'message', 'snapshot', 'key', 'mirror-thread-v1', '3', 'V8_EXACT_ID_HASH_BINDING')")
            execSQL("INSERT INTO backup_verifications (profile_id, account_email, device_id, device_name, mode, started_at, completed_at, status, local_message_count, local_conversation_count, archived_message_count, archived_conversation_count, matched_message_count, missing_message_count, unexpected_archived_message_count, duplicate_fingerprint_count, unreadable_archive_count, verification_percent, short_summary) VALUES ('profile-b', 'mirror@example.test', 'device-b', 'device', 'MIRROR', 1, 2, 'VERIFIED', 1, 1, 1, 1, 1, 0, 0, 0, 0, 100.0, 'verified')")
            close()
        }
        helper.runMigrationsAndValidate(name, 10, true, DatabaseProvider.MIGRATION_9_10)
            .use { database ->
                database.query("SELECT backup_mode FROM account_settings WHERE profile_id = 'profile-a'")
                    .use { it.moveToFirst(); assertEquals("ARCHIVE_APPEND_ONLY", it.getString(0)) }
                database.query("SELECT backup_mode FROM account_settings WHERE profile_id = 'profile-b'")
                    .use { it.moveToFirst(); assertEquals("MIRROR", it.getString(0)) }
                database.query("SELECT snapshot_hash, gmail_message_id FROM conversation_snapshots WHERE profile_id = 'profile-b'")
                    .use {
                        it.moveToFirst()
                        assertEquals("snapshot", it.getString(0))
                        assertEquals("message", it.getString(1))
                    }
                database.query("SELECT status FROM mirror_reconciliation_runs WHERE run_id = 'completed'")
                    .use { it.moveToFirst(); assertEquals("COMPLETED", it.getString(0)) }
                database.query("SELECT state, resulting_gmail_message_id, old_target_proof_version FROM mirror_reconciliation_items WHERE run_id = 'completed'")
                    .use {
                        it.moveToFirst()
                        assertEquals("COMPLETED", it.getString(0))
                        assertEquals("message", it.getString(1))
                        assertEquals("V8_EXACT_ID_HASH_BINDING", it.getString(2))
                    }
                database.query("SELECT status, matched_message_count FROM backup_verifications WHERE profile_id = 'profile-b'")
                    .use {
                        it.moveToFirst()
                        assertEquals("VERIFIED", it.getString(0))
                        assertEquals(1, it.getInt(1))
                    }
                database.query("SELECT count(*) FROM mirror_preview_scans")
                    .use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
                database.query("SELECT count(*) FROM mirror_preview_local_items")
                    .use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
                database.query("SELECT count(*) FROM mirror_preview_remote_items")
                    .use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
    }
    private companion object {
        const val DATABASE_NAME = "migration-3-4"
    }
}
