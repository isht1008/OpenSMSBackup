package io.github.isht1008.opensmsbackup.gmail.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object GmailBackupForegroundPolicy {
    fun shouldPromote(context: Context): Boolean =
        shouldPromote(
            sdkInt = Build.VERSION.SDK_INT,
            notificationPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )

    internal fun shouldPromote(
        sdkInt: Int,
        notificationPermissionGranted: Boolean
    ): Boolean =
        sdkInt < Build.VERSION_CODES.TIRAMISU || notificationPermissionGranted
}
