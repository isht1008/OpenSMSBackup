package io.github.isht1008.opensmsbackup.database

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    private const val DATABASE_NAME =
        "open_sms_backup.db"

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
                        .build()
                        .also { database ->

                            instance = database
                        }
            }
    }
}