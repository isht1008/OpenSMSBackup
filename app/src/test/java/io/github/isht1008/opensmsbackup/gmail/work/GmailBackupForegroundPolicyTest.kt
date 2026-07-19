package io.github.isht1008.opensmsbackup.gmail.work

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupForegroundPolicyTest {
    @Test fun `notification denial skips foreground promotion on Android 13 plus`() {
        assertFalse(
            GmailBackupForegroundPolicy.shouldPromote(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationPermissionGranted = false
            )
        )
    }

    @Test fun `notification grant enables foreground promotion on Android 13 plus`() {
        assertTrue(
            GmailBackupForegroundPolicy.shouldPromote(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationPermissionGranted = true
            )
        )
    }

    @Test fun `foreground promotion does not require runtime permission before Android 13`() {
        assertTrue(
            GmailBackupForegroundPolicy.shouldPromote(
                sdkInt = Build.VERSION_CODES.S,
                notificationPermissionGranted = false
            )
        )
    }
}
