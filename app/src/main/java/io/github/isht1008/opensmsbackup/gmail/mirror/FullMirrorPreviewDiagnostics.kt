package io.github.isht1008.opensmsbackup.gmail.mirror

import android.util.Log
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object FullMirrorPreviewDiagnostics {
    const val TAG = "OpenSMSBackupPreview"
    private const val MILESTONE = 100
    private val lastMilestone = ConcurrentHashMap<String, String>()
    private val lastWorkState = ConcurrentHashMap<String, String>()

    fun digest(value: String): String = MirrorPreviewAccountBinding.digest(value).take(12)

    fun enqueue(scan: MirrorPreviewScanEntity, workId: UUID, continuation: Boolean) {
        event(
            "enqueue scan=${digest(scan.scanId)} work=${digest(workId.toString())} " +
                "scope=${digest(scan.profileId + "\u0000" + scan.deviceId)} continuation=$continuation"
        )
    }

    fun workerStart(scanId: String, workId: UUID, attempt: Int, generation: Int) = event(
        "worker_start scan=${digest(scanId)} work=${digest(workId.toString())} " +
            "attempt=$attempt generation=$generation"
    )

    fun foreground(scanId: String, workId: UUID) = event(
        "foreground_start scan=${digest(scanId)} work=${digest(workId.toString())}"
    )

    fun reconciliation(scan: MirrorPreviewScanEntity, oldState: String, decision: String) = event(
        "startup_reconcile scan=${digest(scan.scanId)} old_work=$oldState decision=$decision " +
            "local=${scan.localProcessed}/${scan.localConversationCount} " +
            "metadata=${scan.metadataChecked}/${scan.remoteDiscovered} " +
            "full=${scan.fullReadsCompleted}/${scan.fullReadsRequired}"
    )

    fun stage(scan: MirrorPreviewScanEntity) = event(
        "stage scan=${digest(scan.scanId)} lifecycle=${scan.lifecycleState} stage=${scan.stage} " +
            "local=${scan.localProcessed}/${scan.localConversationCount} " +
            "metadata=${scan.metadataChecked}/${scan.remoteDiscovered} " +
            "full=${scan.fullReadsCompleted}/${scan.fullReadsRequired} cached=${scan.cachedUnchanged}"
    )

    fun progress(scan: MirrorPreviewScanEntity) {
        val signature = listOf(
            scan.stage,
            scan.localProcessed / MILESTONE,
            scan.metadataChecked / MILESTONE,
            scan.fullReadsCompleted / MILESTONE,
            scan.remoteDiscovered / MILESTONE
        ).joinToString(":")
        if (lastMilestone.put(scan.scanId, signature) != signature) stage(scan)
    }

    fun interrupted(scanId: String, stopReason: Int, explicit: Boolean) = event(
        "worker_interrupted scan=${digest(scanId)} stop_reason=$stopReason explicit_cancel=$explicit"
    )

    fun workState(
        scanId: String,
        workId: UUID,
        state: String,
        attempt: Int,
        generation: Int
    ) {
        val key = digest(workId.toString())
        val signature = "$state:$attempt:$generation"
        if (lastWorkState.put(key, signature) != signature) {
            event(
                "work_state scan=${digest(scanId)} work=$key state=$state " +
                    "attempt=$attempt generation=$generation"
            )
        }
    }

    fun terminal(scan: MirrorPreviewScanEntity, state: String, runId: String? = null) {
        lastMilestone.remove(scan.scanId)
        event(
            "terminal scan=${digest(scan.scanId)} state=$state " +
                "run=${runId?.let(::digest) ?: "none"} uploads=0 trash=0 permanent_delete=0 " +
                "account_a_ops=0"
        )
    }

    fun processReconstruction() = event("process_reconstruction startup_reconciliation=scheduled")

    fun startupFailure() = event(
        "startup_reconcile state=failed category=INFRASTRUCTURE executable_plan=false"
    )

    private fun event(message: String) {
        Log.i(TAG, message)
    }
}
