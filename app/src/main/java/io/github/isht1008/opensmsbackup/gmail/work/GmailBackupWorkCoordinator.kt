package io.github.isht1008.opensmsbackup.gmail.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupRunPlanner
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

sealed interface GmailBackupEnqueueResult {
    data class Enqueued(val workId: UUID) : GmailBackupEnqueueResult
    data class AlreadyRunning(val workId: UUID) : GmailBackupEnqueueResult
    data class Blocked(val reason: String) : GmailBackupEnqueueResult
}

internal interface GmailBackupWorkGateway {
    suspend fun activeGlobalId(): UUID?
    suspend fun activeProfileId(profileId: String): UUID?
    fun enqueueUnique(name: String, request: OneTimeWorkRequest)
    fun observeAll(): Flow<List<WorkInfo>>
    fun cancel(workId: UUID)
}

private class AndroidGmailBackupWorkGateway(
    private val workManager: WorkManager
) : GmailBackupWorkGateway {
    override suspend fun activeGlobalId(): UUID? =
        workManager.getWorkInfosByTagFlow(GmailBackupWorkContract.ALL_WORK_TAG)
            .first()
            .firstOrNull { GmailBackupWorkContract.isActive(it.state) }
            ?.id

    override suspend fun activeProfileId(profileId: String): UUID? =
        workManager
            .getWorkInfosForUniqueWorkFlow(GmailBackupWorkContract.uniqueWorkName(profileId))
            .first()
            .firstOrNull { GmailBackupWorkContract.isActive(it.state) }
            ?.id

    override fun enqueueUnique(name: String, request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    override fun observeAll() =
        workManager.getWorkInfosByTagFlow(GmailBackupWorkContract.ALL_WORK_TAG)

    override fun cancel(workId: UUID) {
        workManager.cancelWorkById(workId)
    }
}

class GmailBackupWorkCoordinator internal constructor(
    private val gateway: GmailBackupWorkGateway,
    private val logger: (String) -> Unit = {}
) {
    constructor(context: Context) : this(
        AndroidGmailBackupWorkGateway(
            WorkManager.getInstance(context.applicationContext)
        ),
        logger = { message -> Log.i("OpenSMSBackup", message) }
    )

    suspend fun enqueueManual(
        profileId: String,
        includeContactNames: Boolean,
        backupScope: GmailBackupScope,
        backupMode: GmailBackupMode
    ): GmailBackupEnqueueResult {
        if (
            backupScope != GmailBackupScope.RECENT_TEST &&
            backupMode == GmailBackupMode.MIRROR
        ) {
            return GmailBackupEnqueueResult.Blocked(
                GmailBackupRunPlanner.FULL_MIRROR_BLOCK_REASON
            )
        }

        gateway.activeGlobalId()?.let {
            return GmailBackupEnqueueResult.AlreadyRunning(it)
        }
        gateway.activeProfileId(profileId)?.let {
            return GmailBackupEnqueueResult.AlreadyRunning(it)
        }

        val input = GmailBackupWorkInput(
            profileId = profileId,
            requestId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            executionMode = BackupExecutionMode.MANUAL,
            includeContactNames = includeContactNames,
            backupScope = backupScope
        )
        val request = OneTimeWorkRequestBuilder<GmailBackupWorker>()
            .setInputData(GmailBackupWorkContract.inputData(input))
            .addTag(GmailBackupWorkContract.ALL_WORK_TAG)
            .addTag(GmailBackupWorkContract.MANUAL_WORK_TAG)
            .addTag(GmailBackupWorkContract.profileTag(profileId))
            .addTag(GmailBackupWorkContract.createdTag(input.createdAt))
            .build()
        val uniqueName = GmailBackupWorkContract.uniqueWorkName(profileId)
        gateway.enqueueUnique(uniqueName, request)
        logger(
            "gmail_work_enqueued work=${request.id} unique=$uniqueName profile=${profileId.take(8)}"
        )
        return GmailBackupEnqueueResult.Enqueued(request.id)
    }

    suspend fun hasActiveWork(profileId: String): Boolean =
        gateway.activeProfileId(profileId) != null

    fun observeAll() = gateway.observeAll()

    fun cancel(workId: UUID) {
        logger("gmail_work_cancel_requested work=$workId")
        gateway.cancel(workId)
    }
}
