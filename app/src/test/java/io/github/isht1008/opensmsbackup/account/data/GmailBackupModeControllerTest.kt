package io.github.isht1008.opensmsbackup.account.data

import io.github.isht1008.opensmsbackup.database.AccountSettingsEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupModeControllerTest {
    @Test fun `new account and invalid storage default to archive`() {
        assertEquals(
            GmailBackupMode.ARCHIVE_APPEND_ONLY.name,
            AccountSettingsEntity(profileId = "new").backupMode
        )
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, GmailBackupMode.fromStorage(null))
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, GmailBackupMode.fromStorage("invalid"))
    }

    @Test fun `existing mirror and profile-specific modes are preserved`() = runBlocking {
        val store = FakeStore(mutableMapOf(
            "personal" to GmailBackupMode.MIRROR,
            "work" to GmailBackupMode.ARCHIVE_APPEND_ONLY
        ))
        val controller = GmailBackupModeController(store)

        controller.load("personal")
        assertEquals(GmailBackupMode.MIRROR, controller.state.mode)
        controller.load("work")
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, controller.state.mode)
        controller.load("personal")
        assertEquals(GmailBackupMode.MIRROR, controller.state.mode)
    }

    @Test fun `archive selection persists immediately`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.MIRROR))
        val controller = GmailBackupModeController(store)
        controller.load("p")

        assertTrue(controller.requestSelection(GmailBackupMode.ARCHIVE_APPEND_ONLY, false))
        assertTrue(controller.saveArchive(false))

        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, store.values["p"])
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, controller.state.mode)
    }

    @Test fun `mirror requires confirmation and cancellation preserves archive`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.ARCHIVE_APPEND_ONLY))
        val controller = GmailBackupModeController(store)
        controller.load("p")

        assertFalse(controller.requestSelection(GmailBackupMode.MIRROR, false))
        assertTrue(controller.state.mirrorConfirmationPending)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, store.values["p"])
        controller.cancelMirrorConfirmation()

        assertFalse(controller.state.mirrorConfirmationPending)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, store.values["p"])
    }

    @Test fun `confirmed mirror persists`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.ARCHIVE_APPEND_ONLY))
        val controller = GmailBackupModeController(store)
        controller.load("p")
        controller.requestSelection(GmailBackupMode.MIRROR, false)

        assertTrue(controller.confirmMirror(false))
        assertEquals(GmailBackupMode.MIRROR, store.values["p"])
    }

    @Test fun `active backup blocks mode changes`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.ARCHIVE_APPEND_ONLY))
        val controller = GmailBackupModeController(store)
        controller.load("p")

        assertFalse(controller.requestSelection(GmailBackupMode.MIRROR, true))
        assertFalse(controller.saveArchive(true))
        assertFalse(controller.state.mirrorConfirmationPending)
        assertEquals(0, store.writeCount)
    }

    @Test fun `stale profile load cannot replace newer profile state`() = runBlocking {
        val delayed = CompletableDeferred<GmailBackupMode>()
        val store = object : GmailBackupModeStore {
            override suspend fun getBackupMode(profileId: String): GmailBackupMode =
                if (profileId == "old") delayed.await() else GmailBackupMode.ARCHIVE_APPEND_ONLY
            override suspend fun setBackupMode(profileId: String, mode: GmailBackupMode) = Unit
        }
        val controller = GmailBackupModeController(store)
        val oldLoad = async(start = CoroutineStart.UNDISPATCHED) { controller.load("old") }
        controller.load("new")
        delayed.complete(GmailBackupMode.MIRROR)
        oldLoad.await()

        assertEquals("new", controller.state.profileId)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, controller.state.mode)
        assertNull(controller.state.errorMessage)
    }

    @Test fun `save completion for old profile cannot overwrite switched profile state`() = runBlocking {
        val saveGate = CompletableDeferred<Unit>()
        val store = object : GmailBackupModeStore {
            override suspend fun getBackupMode(profileId: String) =
                if (profileId == "old") GmailBackupMode.MIRROR else GmailBackupMode.ARCHIVE_APPEND_ONLY
            override suspend fun setBackupMode(profileId: String, mode: GmailBackupMode) {
                saveGate.await()
            }
        }
        val controller = GmailBackupModeController(store)
        controller.load("old")
        val oldSave = async(start = CoroutineStart.UNDISPATCHED) {
            controller.saveArchive(false)
        }
        controller.load("new")
        saveGate.complete(Unit)
        oldSave.await()

        assertEquals("new", controller.state.profileId)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, controller.state.mode)
    }

    private class FakeStore(
        val values: MutableMap<String, GmailBackupMode>
    ) : GmailBackupModeStore {
        var writeCount = 0
        override suspend fun getBackupMode(profileId: String) =
            values[profileId] ?: GmailBackupMode.ARCHIVE_APPEND_ONLY
        override suspend fun setBackupMode(profileId: String, mode: GmailBackupMode) {
            writeCount++
            values[profileId] = mode
        }
    }
}
