package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullMirrorForegroundInfoFactoryTest {
    @Test fun initialAndProgressForegroundInfoAlwaysUseDataSync() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = FullMirrorForegroundInfoFactory(context)
        val initial = factory.create(UUID.randomUUID(), 0, 3_332)
        val progress = factory.create(UUID.randomUUID(), 10, 3_332)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, initial.foregroundServiceType)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, progress.foregroundServiceType)
        assertNotEquals(0, initial.foregroundServiceType)
        assertNotEquals(0, progress.foregroundServiceType)
    }

    @Suppress("DEPRECATION")
    @Test fun manifestRetainsDataSyncPermissionAndWorkManagerServiceType() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        val service = info.services.orEmpty().single { it.name == "androidx.work.impl.foreground.SystemForegroundService" }
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, service.foregroundServiceType)
    }
}