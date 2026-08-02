package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupWorkContract
import io.github.isht1008.opensmsbackup.verification.BackupVerificationWorkContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal data class FullMirrorPreviewRecoveryUiState(
    val scanId: String,
    val progress: FullMirrorPreviewProgress,
    val workId: UUID? = null,
    val publishedRunId: String? = null,
    val pending: Boolean = true
)

internal object FullMirrorPreviewRecoveryState {
    private val mutable = MutableStateFlow<FullMirrorPreviewRecoveryUiState?>(null)
    val state = mutable.asStateFlow()

    fun restoring(scan: MirrorPreviewScanEntity) {
        mutable.value = FullMirrorPreviewRecoveryUiState(
            scan.scanId,
            scan.toPreviewProgress(FullMirrorPreviewStage.RESTORING)
        )
    }

    fun resuming(scan: MirrorPreviewScanEntity, workId: UUID) {
        mutable.value = FullMirrorPreviewRecoveryUiState(
            scan.scanId,
            scan.toPreviewProgress(FullMirrorPreviewStage.RESUMING),
            workId
        )
    }

    fun observing(scan: MirrorPreviewScanEntity, workId: UUID) {
        mutable.value = FullMirrorPreviewRecoveryUiState(
            scan.scanId,
            scan.toPreviewProgress(),
            workId
        )
    }

    fun paused(scan: MirrorPreviewScanEntity) {
        mutable.value = FullMirrorPreviewRecoveryUiState(
            scan.scanId,
            scan.toPreviewProgress(
                if (scan.lifecycleState == FullMirrorPreviewScanState.WAITING_NETWORK.name) {
                    FullMirrorPreviewStage.WAITING_NETWORK
                } else FullMirrorPreviewStage.PAUSED
            )
        )
    }

    fun published(scan: MirrorPreviewScanEntity) {
        val runId = scan.publishedRunId ?: return
        mutable.value = FullMirrorPreviewRecoveryUiState(
            scan.scanId,
            scan.toPreviewProgress(FullMirrorPreviewStage.READY),
            publishedRunId = runId,
            pending = false
        )
    }

    fun clear(scanId: String? = null) {
        if (scanId == null || mutable.value?.scanId == scanId) mutable.value = null
    }
}

internal data class PreviewWorkSnapshot(
    val id: UUID,
    val state: WorkInfo.State,
    val scanId: String?,
    val attempt: Int,
    val generation: Int
)

internal enum class FullMirrorPreviewRecoveryDecision {
    IGNORE_TERMINAL,
    EXPIRE,
    FAIL_BINDING,
    OBSERVE_ACTIVE,
    BLOCK_AMBIGUOUS,
    WAIT_FOR_CONFLICT,
    ENQUEUE_CONTINUATION
}

internal object FullMirrorPreviewRecoveryPolicy {
    private val resumableStates = setOf(
        FullMirrorPreviewScanState.ENQUEUED.name,
        FullMirrorPreviewScanState.RUNNING.name,
        FullMirrorPreviewScanState.WAITING_NETWORK.name,
        FullMirrorPreviewScanState.PAUSED.name,
        FullMirrorPreviewScanState.FINALIZING.name
    )

    fun decide(
        scan: MirrorPreviewScanEntity,
        old: List<PreviewWorkSnapshot>,
        bindingValid: Boolean,
        competingWork: Boolean,
        now: Long
    ): FullMirrorPreviewRecoveryDecision {
        if (scan.lifecycleState !in resumableStates) {
            return FullMirrorPreviewRecoveryDecision.IGNORE_TERMINAL
        }
        if (scan.expiresAt < now) return FullMirrorPreviewRecoveryDecision.EXPIRE
        if (!bindingValid) return FullMirrorPreviewRecoveryDecision.FAIL_BINDING
        val active = old.firstOrNull { FullMirrorPreviewWorkContract.isActive(it.state) }
        if (active != null) {
            return if (active.scanId == scan.scanId) {
                FullMirrorPreviewRecoveryDecision.OBSERVE_ACTIVE
            } else FullMirrorPreviewRecoveryDecision.BLOCK_AMBIGUOUS
        }
        if (competingWork) return FullMirrorPreviewRecoveryDecision.WAIT_FOR_CONFLICT
        return FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION
    }
}

internal object FullMirrorPreviewInterruptionPolicy {
    fun isExplicitCancellation(scan: MirrorPreviewScanEntity?) =
        scan?.lifecycleState == FullMirrorPreviewScanState.CANCELLED.name

    fun shouldCheckpoint(scan: MirrorPreviewScanEntity?) = scan != null &&
        !isExplicitCancellation(scan) &&
        scan.lifecycleState in setOf(
            FullMirrorPreviewScanState.ENQUEUED.name,
            FullMirrorPreviewScanState.RUNNING.name,
            FullMirrorPreviewScanState.WAITING_NETWORK.name,
            FullMirrorPreviewScanState.FINALIZING.name
        )
}

internal interface FullMirrorPreviewRecoveryGateway {
    suspend fun unique(name: String): List<PreviewWorkSnapshot>
    suspend fun active(tag: String): List<PreviewWorkSnapshot>
    fun enqueue(name: String, scan: MirrorPreviewScanEntity): UUID
}

internal data class FullMirrorPreviewRecoveryOutcome(
    val decision: FullMirrorPreviewRecoveryDecision,
    val oldState: String,
    val workId: UUID? = null
)

internal class FullMirrorPreviewRecoveryEngine(
    private val gateway: FullMirrorPreviewRecoveryGateway
) {
    suspend fun reconcile(
        scan: MirrorPreviewScanEntity,
        bindingValid: Boolean,
        competingWork: Boolean,
        now: Long
    ): FullMirrorPreviewRecoveryOutcome {
        val uniqueName = FullMirrorPreviewWorkContract.uniqueWorkName(
            scan.profileId,
            scan.deviceId
        )
        val old = gateway.unique(uniqueName)
        val decision = FullMirrorPreviewRecoveryPolicy.decide(
            scan,
            old,
            bindingValid,
            competingWork,
            now
        )
        val active = old.firstOrNull { FullMirrorPreviewWorkContract.isActive(it.state) }
        val workId = when (decision) {
            FullMirrorPreviewRecoveryDecision.OBSERVE_ACTIVE,
            FullMirrorPreviewRecoveryDecision.BLOCK_AMBIGUOUS -> active?.id
            FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION ->
                gateway.enqueue(uniqueName, scan)
            else -> null
        }
        return FullMirrorPreviewRecoveryOutcome(
            decision,
            old.maxByOrNull { it.generation }?.state?.name ?: "MISSING",
            workId
        )
    }
}

private class AndroidFullMirrorPreviewRecoveryGateway(
    private val workManager: WorkManager
) : FullMirrorPreviewRecoveryGateway {
    override suspend fun unique(name: String) =
        workManager.getWorkInfosForUniqueWorkFlow(name).first().map(::snapshot)

    override suspend fun active(tag: String) = workManager.getWorkInfosByTagFlow(tag).first()
        .filter { FullMirrorPreviewWorkContract.isActive(it.state) }
        .map(::snapshot)

    override fun enqueue(name: String, scan: MirrorPreviewScanEntity): UUID {
        val request = FullMirrorPreviewRequestFactory.create(scan)
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        FullMirrorPreviewDiagnostics.enqueue(scan, request.id, continuation = true)
        return request.id
    }

    private fun snapshot(info: WorkInfo) = PreviewWorkSnapshot(
        info.id,
        info.state,
        FullMirrorPreviewWorkContract.scanId(info.tags),
        info.runAttemptCount,
        info.generation
    )
}

internal class FullMirrorPreviewStartupReconciler(
    private val context: Context,
    private val gateway: FullMirrorPreviewRecoveryGateway =
        AndroidFullMirrorPreviewRecoveryGateway(
            WorkManager.getInstance(context.applicationContext)
        )
) {
    private val database = DatabaseProvider.getDatabase(context.applicationContext)
    private val dao = database.mirrorPreviewScanDao()
    private val engine = FullMirrorPreviewRecoveryEngine(gateway)

    suspend fun reconcile() = processMutex.withLock {
        val now = System.currentTimeMillis()
        dao.expireAbandoned(now)
        dao.findPublishedAwaitingConfirmation(now)?.let {
            FullMirrorPreviewRecoveryState.published(it)
        }
        val candidates = dao.findRecoveryCandidates(MAX_RECOVERY_SCANS)
        if (candidates.isEmpty()) return@withLock
        candidates.forEach { scan ->
            FullMirrorPreviewRecoveryState.restoring(scan)
            val competing = hasCompetingWork(scan.scanId)
            val outcome = engine.reconcile(
                scan,
                bindingIsValid(scan),
                competing,
                now
            )
            when (outcome.decision) {
                FullMirrorPreviewRecoveryDecision.OBSERVE_ACTIVE -> {
                    val workId = requireNotNull(outcome.workId)
                    FullMirrorPreviewRecoveryState.observing(scan, workId)
                    FullMirrorPreviewDiagnostics.reconciliation(
                        scan,
                        outcome.oldState,
                        "observe"
                    )
                }
                FullMirrorPreviewRecoveryDecision.BLOCK_AMBIGUOUS -> {
                    failSafely(scan, "RECOVERY_AMBIGUOUS_WORK")
                    FullMirrorPreviewDiagnostics.reconciliation(
                        scan,
                        outcome.oldState,
                        "ambiguous_active_work"
                    )
                }
                FullMirrorPreviewRecoveryDecision.WAIT_FOR_CONFLICT -> {
                    FullMirrorPreviewRecoveryState.paused(scan)
                    FullMirrorPreviewDiagnostics.reconciliation(
                        scan,
                        outcome.oldState,
                        "conflict_wait"
                    )
                }
                FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION -> {
                    val workId = requireNotNull(outcome.workId)
                    FullMirrorPreviewRecoveryState.resuming(scan, workId)
                    FullMirrorPreviewDiagnostics.reconciliation(
                        scan,
                        outcome.oldState,
                        "continue"
                    )
                }
                FullMirrorPreviewRecoveryDecision.FAIL_BINDING -> {
                    failSafely(scan, "RECOVERY_BINDING_MISMATCH")
                    FullMirrorPreviewDiagnostics.reconciliation(
                        scan,
                        outcome.oldState,
                        "binding_mismatch"
                    )
                }
                FullMirrorPreviewRecoveryDecision.EXPIRE -> {
                    dao.expireAbandoned(now)
                    FullMirrorPreviewRecoveryState.clear(scan.scanId)
                }
                FullMirrorPreviewRecoveryDecision.IGNORE_TERMINAL -> Unit
            }
        }
    }

    private suspend fun bindingIsValid(scan: MirrorPreviewScanEntity): Boolean {
        val profile = database.accountProfileDao().findProfileById(scan.profileId) ?: return false
        val settings = database.accountProfileDao().findSettings(scan.profileId) ?: return false
        if (profile.connectionState != "CONNECTED") return false
        if (settings.backupMode != GmailBackupMode.MIRROR.name) return false
        if (scan.expectedPolicy != GmailBackupMode.MIRROR.name) return false
        if (MirrorPreviewAccountBinding.fingerprint(profile.profileId, profile.accountEmail) !=
            scan.accountFingerprint
        ) return false
        val store = DeviceProfileStore.create(context)
        if (store.getOrCreate().deviceId != scan.deviceId) return false
        return store.getGmailDeviceLabelId(scan.profileId) == scan.deviceLabelId
    }

    private suspend fun hasCompetingWork(scanId: String): Boolean {
        val conflictTags = listOf(
            GmailBackupWorkContract.ALL_WORK_TAG,
            FullMirrorWorkContract.ALL_WORK_TAG,
            BackupVerificationWorkContract.TAG
        )
        if (conflictTags.any { gateway.active(it).isNotEmpty() }) return true
        return gateway.active(FullMirrorPreviewWorkContract.ALL_WORK_TAG)
            .any { it.scanId != scanId }
    }

    private suspend fun failSafely(scan: MirrorPreviewScanEntity, category: String) {
        check(dao.updateScan(scan.copy(
            lifecycleState = FullMirrorPreviewScanState.FAILED.name,
            stage = FullMirrorPreviewStage.PAUSED.name,
            updatedAt = System.currentTimeMillis(),
            lastErrorCategory = category
        )) == 1)
        FullMirrorPreviewRecoveryState.clear(scan.scanId)
    }

    companion object {
        const val MAX_RECOVERY_SCANS = 8
        private val processMutex = Mutex()
    }
}
