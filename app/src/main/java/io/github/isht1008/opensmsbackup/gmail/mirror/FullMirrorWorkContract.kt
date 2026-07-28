package io.github.isht1008.opensmsbackup.gmail.mirror

import androidx.work.Data

data class FullMirrorWorkInput(val runId: String, val profileId: String, val deviceId: String, val createdAt: Long)
data class FullMirrorWorkProgress(
    val runId: String, val profileId: String, val maskedAccount: String, val checked: Int, val total: Int,
    val unchanged: Int, val recoveries: Int, val conflicts: Int,
    val newUploaded: Int, val changedReplaced: Int, val previousTrashed: Int,
    val remoteOnlyTrashed: Int, val warnings: Int, val failed: Int, val remaining: Int,
    val elapsedMillis: Long, val etaSeconds: Long? = null, val phase: String
)
data class FullMirrorWorkResult(
    val runId: String, val profileId: String, val maskedAccount: String, val state: String, val durationMillis: Long,
    val previewAgeMillis: Long, val ownedRemote: Int, val unchanged: Int, val newUploaded: Int,
    val changedReplaced: Int, val previousTrashed: Int, val remoteOnlyTrashed: Int,
    val warnings: Int, val recoveries: Int, val conflicts: Int, val failed: Int, val remaining: Int, val resumable: Boolean
)

object FullMirrorWorkContract {
    const val ALL_WORK_TAG = "full-mirror-work"
    fun profileTag(profileId: String) = "full-mirror-profile-$profileId"
    fun runTag(runId: String) = "full-mirror-run-$runId"
    fun uniqueWorkName(profileId: String, deviceId: String, runId: String) = "full-mirror-$profileId-$deviceId-$runId"
    fun inputData(v: FullMirrorWorkInput) = Data.Builder().putString("mirror.run_id", v.runId)
        .putString("mirror.profile_id", v.profileId).putString("mirror.device_id", v.deviceId)
        .putLong("mirror.created_at", v.createdAt).build()
    fun readInput(d: Data): FullMirrorWorkInput? {
        val run = d.getString("mirror.run_id")?.takeIf(String::isNotBlank) ?: return null
        val profile = d.getString("mirror.profile_id")?.takeIf(String::isNotBlank) ?: return null
        val device = d.getString("mirror.device_id")?.takeIf(String::isNotBlank) ?: return null
        val created = d.getLong("mirror.created_at", 0).takeIf { it > 0 } ?: return null
        return FullMirrorWorkInput(run, profile, device, created)
    }
    fun progressData(v: FullMirrorWorkProgress): Data {
        val b = Data.Builder().putString("mirror.run_id", v.runId).putString("mirror.profile_id", v.profileId)
            .putString("mirror.masked_account", v.maskedAccount)
            .putInt("mirror.checked", v.checked).putInt("mirror.total", v.total)
            .putInt("mirror.unchanged", v.unchanged).putInt("mirror.recoveries", v.recoveries).putInt("mirror.conflicts", v.conflicts)
            .putInt("mirror.new_uploaded", v.newUploaded).putInt("mirror.changed_replaced", v.changedReplaced)
            .putInt("mirror.previous_trashed", v.previousTrashed).putInt("mirror.remote_only_trashed", v.remoteOnlyTrashed)
            .putInt("mirror.warnings", v.warnings).putInt("mirror.failed", v.failed)
            .putInt("mirror.remaining", v.remaining).putLong("mirror.elapsed", v.elapsedMillis)
            .putString("mirror.phase", v.phase)
        v.etaSeconds?.let { b.putLong("mirror.eta", it) }
        return b.build()
    }
    fun readProgress(d: Data): FullMirrorWorkProgress? {
        val run = d.getString("mirror.run_id") ?: return null
        val profile = d.getString("mirror.profile_id") ?: return null
        return FullMirrorWorkProgress(run, profile, d.getString("mirror.masked_account").orEmpty(), d.getInt("mirror.checked", 0), d.getInt("mirror.total", 0),
            d.getInt("mirror.unchanged", 0), d.getInt("mirror.recoveries", 0), d.getInt("mirror.conflicts", 0),
            d.getInt("mirror.new_uploaded", 0), d.getInt("mirror.changed_replaced", 0),
            d.getInt("mirror.previous_trashed", 0), d.getInt("mirror.remote_only_trashed", 0),
            d.getInt("mirror.warnings", 0), d.getInt("mirror.failed", 0), d.getInt("mirror.remaining", 0),
            d.getLong("mirror.elapsed", 0), d.getLong("mirror.eta", -1).takeIf { it >= 0 },
            d.getString("mirror.phase") ?: "Preparing approved Mirror plan")
    }
    fun outputData(v: FullMirrorWorkResult) = Data.Builder()
        .putString("mirror.result.run_id", v.runId).putString("mirror.result.profile_id", v.profileId)
        .putString("mirror.result.masked_account", v.maskedAccount)
        .putString("mirror.result.state", v.state).putLong("mirror.result.duration", v.durationMillis)
        .putLong("mirror.result.preview_age", v.previewAgeMillis).putInt("mirror.result.owned", v.ownedRemote)
        .putInt("mirror.result.unchanged", v.unchanged).putInt("mirror.result.new", v.newUploaded)
        .putInt("mirror.result.replaced", v.changedReplaced).putInt("mirror.result.previous_trashed", v.previousTrashed)
        .putInt("mirror.result.remote_only_trashed", v.remoteOnlyTrashed).putInt("mirror.result.warnings", v.warnings)
        .putInt("mirror.result.recoveries", v.recoveries).putInt("mirror.result.conflicts", v.conflicts).putInt("mirror.result.failed", v.failed)
        .putInt("mirror.result.remaining", v.remaining).putBoolean("mirror.result.resumable", v.resumable).build()
    fun readResult(d: Data): FullMirrorWorkResult? {
        val run = d.getString("mirror.result.run_id") ?: return null
        val profile = d.getString("mirror.result.profile_id") ?: return null
        return FullMirrorWorkResult(run, profile, d.getString("mirror.result.masked_account").orEmpty(), d.getString("mirror.result.state") ?: "FAILED",
            d.getLong("mirror.result.duration", 0), d.getLong("mirror.result.preview_age", 0),
            d.getInt("mirror.result.owned", 0), d.getInt("mirror.result.unchanged", 0),
            d.getInt("mirror.result.new", 0), d.getInt("mirror.result.replaced", 0),
            d.getInt("mirror.result.previous_trashed", 0), d.getInt("mirror.result.remote_only_trashed", 0),
            d.getInt("mirror.result.warnings", 0), d.getInt("mirror.result.recoveries", 0), d.getInt("mirror.result.conflicts", 0),
            d.getInt("mirror.result.failed", 0), d.getInt("mirror.result.remaining", 0),
            d.getBoolean("mirror.result.resumable", false))
    }
}
