package io.github.isht1008.opensmsbackup.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupVerificationDaoTest {
    @Test fun resultSavesAndReloadsWithoutSmsContent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BackupDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val entity = BackupVerificationEntity(profileId = "p", accountEmail = "user@example.com",
                deviceId = "d", deviceName = "Phone", mode = "MIRROR", startedAt = 1,
                completedAt = 2, status = "VERIFIED", localMessageCount = 1,
                localConversationCount = 1, archivedMessageCount = 1, archivedConversationCount = 1,
                matchedMessageCount = 1, missingMessageCount = 0, unexpectedArchivedMessageCount = 0,
                duplicateFingerprintCount = 0, unreadableArchiveCount = 0,
                verificationPercent = 100.0, shortSummary = "All messages represented")
            val id = db.backupVerificationDao().insert(entity)
            val loaded = db.backupVerificationDao().findById(id)
            assertNotNull(loaded)
            assertEquals("VERIFIED", loaded?.status)
            assertFalse(loaded.toString().contains("message body"))
        } finally { db.close() }
    }
}
