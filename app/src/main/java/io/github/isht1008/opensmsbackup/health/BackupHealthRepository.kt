package io.github.isht1008.opensmsbackup.health

import android.content.Context
import io.github.isht1008.opensmsbackup.backup.BackupHistoryRepository
import io.github.isht1008.opensmsbackup.database.BackupVerificationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupHealthRepository(
    private val dao: BackupVerificationDao,
    private val localRepository: BackupHistoryRepository = BackupHistoryRepository()
) {
    fun observeVerificationHistory() = dao.observeHistory()
    suspend fun loadLocalBackups(context: Context) = withContext(Dispatchers.IO) {
        localRepository.getBackups(context)
    }
    suspend fun findVerification(id: Long) = dao.findById(id)
}
