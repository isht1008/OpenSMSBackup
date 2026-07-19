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

    private companion object {
        const val DATABASE_NAME = "migration-3-4"
    }
}
