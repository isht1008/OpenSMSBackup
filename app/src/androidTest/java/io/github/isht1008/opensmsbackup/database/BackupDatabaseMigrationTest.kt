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

    private companion object {
        const val DATABASE_NAME = "migration-3-4"
    }
}
