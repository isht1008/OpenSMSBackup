package io.github.isht1008.opensmsbackup.gmail.mirror

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import java.util.concurrent.TimeUnit

internal object FullMirrorPreviewRequestFactory {
    fun create(
        scan: MirrorPreviewScanEntity,
        now: Long = System.currentTimeMillis()
    ): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<FullMirrorPreviewWorker>()
            .setInputData(FullMirrorPreviewWorkContract.inputData(
                FullMirrorPreviewWorkInput(
                    scan.scanId,
                    scan.profileId,
                    scan.deviceId,
                    scan.createdAt
                )
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(FullMirrorPreviewWorkContract.ALL_WORK_TAG)
            .addTag(FullMirrorPreviewWorkContract.profileTag(scan.profileId))
            .addTag(FullMirrorPreviewWorkContract.scanTag(scan.scanId))
            .addTag(FullMirrorPreviewWorkContract.createdTag(scan.createdAt))
        scan.retryAt?.let { retryAt ->
            (retryAt - now).coerceAtLeast(0L).takeIf { it > 0L }?.let {
                builder.setInitialDelay(it, TimeUnit.MILLISECONDS)
            }
        }
        return builder.build()
    }
}
