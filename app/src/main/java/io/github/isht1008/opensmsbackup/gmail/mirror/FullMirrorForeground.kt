package io.github.isht1008.opensmsbackup.gmail.mirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal class FullMirrorForegroundInfoFactory(private val context: Context) {
    fun create(workId: UUID, checked: Int, total: Int): ForegroundInfo {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "OpenSMSBackup Full Mirror", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(io.github.isht1008.opensmsbackup.R.drawable.ic_launcher_foreground)
            .setContentTitle("Full Mirror synchronization")
            .setContentText("$checked of $total approved actions")
            .setProgress(total.coerceAtLeast(1), checked.coerceAtMost(total), total <= 0)
            .setOngoing(true)
            .addAction(0, "Cancel", WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val SERVICE_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        private const val CHANNEL = "opensmsbackup-full-mirror"
        private const val NOTIFICATION_ID = 4102
    }
}

internal object FullMirrorForegroundStartBoundary {
    suspend fun enter(startForeground: suspend () -> Unit, recordRejectedStart: suspend () -> Unit): Boolean = try {
        startForeground()
        true
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Throwable) {
        recordRejectedStart()
        false
    }
}