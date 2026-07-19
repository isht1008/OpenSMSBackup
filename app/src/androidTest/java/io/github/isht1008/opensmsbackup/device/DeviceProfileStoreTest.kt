package io.github.isht1008.opensmsbackup.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceProfileStoreTest {
    @Test fun deviceIdIsGeneratedOnceAndSurvivesSettingsEdits() = runBlocking {
        val store = DeviceProfileStore.create(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val first = store.getOrCreate()
        val second = store.getOrCreate()
        val edited = store.update("Test Phone", "+1 555 123 4567", null)

        assertNotNull(java.util.UUID.fromString(first.deviceId))
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(first.deviceId, edited.deviceId)
        assertEquals("Test Phone", edited.displayName)
        assertEquals("•••••34567", edited.maskedPrimaryPhoneNumber)
    }
}
