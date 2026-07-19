package io.github.isht1008.opensmsbackup.gmail.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

class GmailBackupNotificationFactory(
    private val context: Context
) {
    fun foregroundInfo(
        workId: UUID,
        progress: GmailBackupWorkProgress
    ): ForegroundInfo {
        createChannel()

        val notification = notification(workId, progress)

        return ForegroundInfo(
            notificationId(workId),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    internal fun notification(
        workId: UUID,
        progress: GmailBackupWorkProgress
    ): android.app.Notification {
        val percent = progress.fraction?.let { (it * 100).toInt().coerceIn(0, 100) }
        val details = buildString {
            append(progress.statusMessage)
            if (progress.total > 0) {
                append(" · ${progress.checked}/${progress.total}")
            }
            append(" · Uploaded ${progress.uploaded}")
            append(" · Unchanged ${progress.unchanged}")
            append(" · Failed ${progress.failed}")
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("OpenSMSBackup Gmail Backup")
            .setContentText(progress.accountEmail ?: "Preparing backup")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel backup",
                WorkManager.getInstance(context).createCancelPendingIntent(workId)
            )
            .build()
    }

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "OpenSMSBackup – Gmail Backup",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress for active Gmail conversation backups"
            }
        )
    }

    private fun notificationId(workId: UUID): Int =
        NOTIFICATION_ID_BASE + (workId.hashCode() and 0x0FFF)

    companion object {
        const val CHANNEL_ID = "gmail_backup_progress"
        private const val NOTIFICATION_ID_BASE = 24_000
    }
}
