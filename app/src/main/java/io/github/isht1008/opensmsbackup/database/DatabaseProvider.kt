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
                            MIGRATION_1_2
                        )
                        .build()
                        .also { database ->
                            instance = database
                        }
            }
    }
}
