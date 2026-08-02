package io.github.isht1008.opensmsbackup.gmail.mirror

import androidx.work.Data
import androidx.work.WorkInfo

enum class FullMirrorPreviewScanState {
    ENQUEUED, RUNNING, WAITING_NETWORK, PAUSED, FINALIZING, PUBLISHED,
    CANCELLED, FAILED, EXPIRED, SUPERSEDED
}

enum class FullMirrorPreviewStage {
    RESTORING,
    RESUMING,
    PREPARING,
    READING_LOCAL_SMS,
    BUILDING_LOCAL_INDEX,
    LISTING_GMAIL,
    CHECKING_METADATA,
    READING_REQUIRED_SNAPSHOTS,
    WAITING_NETWORK,
    FINALIZING,
    PAUSED,
    READY
}

data class FullMirrorPreviewWorkInput(
    val scanId: String,
    val profileId: String,
    val deviceId: String,
    val createdAt: Long
)

data class FullMirrorPreviewProgress(
    val scanId: String,
    val stage: FullMirrorPreviewStage,
    val localProcessed: Int = 0,
    val localTotal: Int = 0,
    val remoteDiscovered: Int = 0,
    val metadataChecked: Int = 0,
    val fullReadsCompleted: Int = 0,
    val fullReadsRequired: Int = 0,
    val cachedUnchanged: Int = 0,
    val retryAttempt: Int = 0,
    val retryDelayMillis: Long? = null,
    val elapsedMillis: Long = 0
)

data class FullMirrorPreviewWorkResult(
    val scanId: String,
    val runId: String?,
    val state: FullMirrorPreviewScanState,
    val safeErrorCategory: String? = null
)

object FullMirrorPreviewWorkContract {
    const val ALL_WORK_TAG = "full-mirror-preview-work"
    const val RETENTION_MILLIS = 24L * 60L * 60L * 1_000L

    fun profileTag(profileId: String) =
        "full-mirror-preview-profile-${MirrorPreviewAccountBinding.digest(profileId)}"
    fun scanTag(scanId: String) = "full-mirror-preview-scan-$scanId"
    fun createdTag(createdAt: Long) = "full-mirror-preview-created-$createdAt"
    fun uniqueWorkName(profileId: String, deviceId: String) =
        "full-mirror-preview-${MirrorPreviewAccountBinding.digest("$profileId\u0000$deviceId")}"

    fun scanId(tags: Set<String>): String? = tags.firstNotNullOfOrNull { tag ->
        tag.removePrefix("full-mirror-preview-scan-")
            .takeIf { tag.startsWith("full-mirror-preview-scan-") && it.isNotBlank() }
    }

    fun createdAt(tags: Set<String>): Long = tags.firstNotNullOfOrNull { tag ->
        tag.removePrefix("full-mirror-preview-created-")
            .takeIf { tag.startsWith("full-mirror-preview-created-") }
            ?.toLongOrNull()
    } ?: 0L

    fun isActive(state: WorkInfo.State) =
        state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING ||
            state == WorkInfo.State.BLOCKED

    fun inputData(value: FullMirrorPreviewWorkInput): Data = Data.Builder()
        .putString("preview.scan_id", value.scanId)
        .putString("preview.profile_id", value.profileId)
        .putString("preview.device_id", value.deviceId)
        .putLong("preview.created_at", value.createdAt)
        .build()

    fun readInput(data: Data): FullMirrorPreviewWorkInput? {
        val scanId = data.getString("preview.scan_id")?.takeIf(String::isNotBlank) ?: return null
        val profileId = data.getString("preview.profile_id")?.takeIf(String::isNotBlank) ?: return null
        val deviceId = data.getString("preview.device_id")?.takeIf(String::isNotBlank) ?: return null
        val createdAt = data.getLong("preview.created_at", 0L).takeIf { it > 0L } ?: return null
        return FullMirrorPreviewWorkInput(scanId, profileId, deviceId, createdAt)
    }

    fun progressData(value: FullMirrorPreviewProgress): Data {
        val builder = Data.Builder()
            .putString("preview.progress.scan_id", value.scanId)
            .putString("preview.progress.stage", value.stage.name)
            .putInt("preview.progress.local_processed", value.localProcessed)
            .putInt("preview.progress.local_total", value.localTotal)
            .putInt("preview.progress.remote_discovered", value.remoteDiscovered)
            .putInt("preview.progress.metadata_checked", value.metadataChecked)
            .putInt("preview.progress.full_completed", value.fullReadsCompleted)
            .putInt("preview.progress.full_required", value.fullReadsRequired)
            .putInt("preview.progress.cached_unchanged", value.cachedUnchanged)
            .putInt("preview.progress.retry_attempt", value.retryAttempt)
            .putLong("preview.progress.elapsed", value.elapsedMillis)
        value.retryDelayMillis?.let { builder.putLong("preview.progress.retry_delay", it) }
        return builder.build()
    }

    fun readProgress(data: Data): FullMirrorPreviewProgress? {
        val scanId = data.getString("preview.progress.scan_id") ?: return null
        val stage = data.getString("preview.progress.stage")
            ?.let { runCatching { FullMirrorPreviewStage.valueOf(it) }.getOrNull() }
            ?: return null
        return FullMirrorPreviewProgress(
            scanId = scanId,
            stage = stage,
            localProcessed = data.getInt("preview.progress.local_processed", 0),
            localTotal = data.getInt("preview.progress.local_total", 0),
            remoteDiscovered = data.getInt("preview.progress.remote_discovered", 0),
            metadataChecked = data.getInt("preview.progress.metadata_checked", 0),
            fullReadsCompleted = data.getInt("preview.progress.full_completed", 0),
            fullReadsRequired = data.getInt("preview.progress.full_required", 0),
            cachedUnchanged = data.getInt("preview.progress.cached_unchanged", 0),
            retryAttempt = data.getInt("preview.progress.retry_attempt", 0),
            retryDelayMillis = data.getLong("preview.progress.retry_delay", -1L).takeIf { it >= 0L },
            elapsedMillis = data.getLong("preview.progress.elapsed", 0L)
        )
    }

    fun outputData(value: FullMirrorPreviewWorkResult): Data {
        val builder = Data.Builder()
            .putString("preview.result.scan_id", value.scanId)
            .putString("preview.result.state", value.state.name)
        value.runId?.let { builder.putString("preview.result.run_id", it) }
        value.safeErrorCategory?.let { builder.putString("preview.result.error", it) }
        return builder.build()
    }

    fun readResult(data: Data): FullMirrorPreviewWorkResult? {
        val scanId = data.getString("preview.result.scan_id") ?: return null
        val state = data.getString("preview.result.state")
            ?.let { runCatching { FullMirrorPreviewScanState.valueOf(it) }.getOrNull() }
            ?: return null
        return FullMirrorPreviewWorkResult(
            scanId,
            data.getString("preview.result.run_id"),
            state,
            data.getString("preview.result.error")
        )
    }
}

object FullMirrorPreviewProgressText {
    fun title(stage: FullMirrorPreviewStage): String = when (stage) {
        FullMirrorPreviewStage.RESTORING -> "Restoring Full Mirror preview"
        FullMirrorPreviewStage.RESUMING -> "Resuming Full Mirror preview"
        FullMirrorPreviewStage.PREPARING -> "Preparing Full Mirror preview"
        FullMirrorPreviewStage.READING_LOCAL_SMS -> "Reading SMS from device"
        FullMirrorPreviewStage.BUILDING_LOCAL_INDEX -> "Building local conversation index"
        FullMirrorPreviewStage.LISTING_GMAIL -> "Listing owned Gmail snapshots"
        FullMirrorPreviewStage.CHECKING_METADATA -> "Checking Gmail snapshot metadata"
        FullMirrorPreviewStage.READING_REQUIRED_SNAPSHOTS -> "Reading required Gmail snapshots"
        FullMirrorPreviewStage.WAITING_NETWORK -> "Waiting for network"
        FullMirrorPreviewStage.FINALIZING -> "Finalizing Full Mirror preview"
        FullMirrorPreviewStage.PAUSED -> "Full Mirror preview paused"
        FullMirrorPreviewStage.READY -> "Full Mirror preview ready"
    }

    fun details(progress: FullMirrorPreviewProgress): String = buildString {
        append(title(progress.stage))
        if (progress.localTotal > 0) {
            append("\nLocal conversations: ${progress.localProcessed} / ${progress.localTotal}")
        }
        if (progress.remoteDiscovered > 0 || progress.metadataChecked > 0) {
            append("\nGmail metadata: ${progress.metadataChecked} / ${progress.remoteDiscovered}")
        }
        if (progress.fullReadsRequired > 0) {
            append("\nRequired snapshots: ${progress.fullReadsCompleted} / ${progress.fullReadsRequired}")
        }
        if (progress.cachedUnchanged > 0) {
            append("\nCached unchanged: ${progress.cachedUnchanged}")
        }
        if (progress.retryAttempt > 0) {
            append("\nRetry: ${progress.retryAttempt}")
            progress.retryDelayMillis?.let {
                append(" in ${(it + 999L) / 1_000L}s")
            }
        }
        append("\nElapsed: ${formatDuration(progress.elapsedMillis)}")
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1_000L).coerceAtLeast(0L)
        return if (seconds < 60L) "${seconds}s"
        else "${seconds / 60L}m ${seconds % 60L}s"
    }
}

internal fun io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity.toPreviewProgress(
    stageOverride: FullMirrorPreviewStage? = null,
    now: Long = System.currentTimeMillis()
) = FullMirrorPreviewProgress(
    scanId = scanId,
    stage = stageOverride ?: runCatching { FullMirrorPreviewStage.valueOf(stage) }
        .getOrDefault(FullMirrorPreviewStage.PAUSED),
    localProcessed = localProcessed,
    localTotal = localConversationCount,
    remoteDiscovered = remoteDiscovered,
    metadataChecked = metadataChecked,
    fullReadsCompleted = fullReadsCompleted,
    fullReadsRequired = fullReadsRequired,
    cachedUnchanged = cachedUnchanged,
    retryAttempt = retryAttempt,
    retryDelayMillis = retryAt?.let { (it - now).coerceAtLeast(0L) },
    elapsedMillis = (now - createdAt).coerceAtLeast(0L)
)
