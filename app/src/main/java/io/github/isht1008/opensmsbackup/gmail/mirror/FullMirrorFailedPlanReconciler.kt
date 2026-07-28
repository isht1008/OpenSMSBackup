package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationRunEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore

class FullMirrorFailedPlanReconciler(private val context: Context) {
    data class Outcome(val runId: String, val terminalized: Boolean, val reason: String)

    suspend fun reconcile(infos: List<WorkInfo>): List<Outcome> {
        if (infos.any { !it.state.isFinished }) return emptyList()
        val dao = DatabaseProvider.getDatabase(context).mirrorReconciliationDao()
        val deviceId = DeviceProfileStore.create(context).getOrCreate().deviceId
        val confirmed = dao.findConfirmedRuns(deviceId)
        return confirmed.map { run ->
            val profileWork = infos.filter { FullMirrorWorkContract.profileTag(run.profileId) in it.tags }
            val exactWork = profileWork.filter { FullMirrorWorkContract.runTag(run.runId) in it.tags }
            val candidates = when {
                exactWork.isNotEmpty() -> exactWork
                confirmed.count { it.profileId == run.profileId } == 1 && profileWork.size == 1 -> profileWork
                else -> emptyList()
            }
            val evidence = Evidence(
                run,
                candidates.map { it.state },
                dao.itemCount(run.runId),
                dao.mutationOrAmbiguousItemCount(run.runId)
            )
            if (!Policy.canTerminalize(evidence)) {
                Outcome(run.runId, false, "RECOVERY_REVIEW_REQUIRED")
            } else {
                val updated = dao.failConfirmedForegroundStartIfAllPending(
                    run.runId,
                    run.profileId,
                    System.currentTimeMillis()
                )
                Log.i(
                    "OpenSMSBackup",
                    "full_mirror_failed_plan_reconciliation terminalized=${updated == 1} " +
                        "total=${evidence.itemCount} pending=${evidence.itemCount} " +
                        "mutation_or_ambiguous=${evidence.mutationOrAmbiguousCount}"
                )
                Outcome(
                    run.runId,
                    updated == 1,
                    if (updated == 1) "FOREGROUND_START_FAILED" else "RECOVERY_REVIEW_REQUIRED"
                )
            }
        }
    }

    internal data class Evidence(
        val run: MirrorReconciliationRunEntity,
        val workStates: List<WorkInfo.State>,
        val itemCount: Int,
        val mutationOrAmbiguousCount: Int
    )

    internal object Policy {
        fun canTerminalize(evidence: Evidence): Boolean =
            evidence.run.status == FullMirrorRunStatus.CONFIRMED.name &&
                evidence.itemCount > 0 &&
                evidence.mutationOrAmbiguousCount == 0 &&
                evidence.workStates.size == 1 &&
                evidence.workStates.single() == WorkInfo.State.FAILED
    }
}