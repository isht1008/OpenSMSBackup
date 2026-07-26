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
    @Test fun `archive to mirror requires confirmation`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.ARCHIVE_APPEND_ONLY))
        val controller = GmailBackupModeController(store)
        controller.load("p")

        assertTrue(controller.openPolicyWizard(false))
        controller.choosePendingMode(GmailBackupMode.MIRROR)
        assertEquals(0, store.writeCount)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, controller.state.mode)
        assertTrue(controller.confirmPolicyChange(false))

        assertEquals(GmailBackupMode.MIRROR, store.values["p"])
    }

    @Test fun `mirror to archive requires confirmation`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.MIRROR))
        val controller = GmailBackupModeController(store)
        controller.load("p")
        controller.openPolicyWizard(false)
        controller.choosePendingMode(GmailBackupMode.ARCHIVE_APPEND_ONLY)

        assertEquals(GmailBackupMode.MIRROR, store.values["p"])
        assertTrue(controller.confirmPolicyChange(false))
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, store.values["p"])
    }

    @Test fun `cancelling wizard keeps current policy`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.ARCHIVE_APPEND_ONLY))
        val controller = GmailBackupModeController(store)
        controller.load("p")
        controller.openPolicyWizard(false)
        controller.choosePendingMode(GmailBackupMode.MIRROR)

        controller.cancelPolicyWizard()

        assertFalse(controller.state.policyWizardOpen)
        assertNull(controller.state.pendingMode)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, store.values["p"])
        assertEquals(0, store.writeCount)
    }

    @Test fun `active backup prevents wizard and persistence`() = runBlocking {
        val store = FakeStore(mutableMapOf("p" to GmailBackupMode.ARCHIVE_APPEND_ONLY))
        val controller = GmailBackupModeController(store)
        controller.load("p")

        assertFalse(controller.openPolicyWizard(true))
        assertFalse(controller.confirmPolicyChange(true))
        assertEquals(0, store.writeCount)
    }

    @Test fun `multiple accounts retain isolated policies`() = runBlocking {
        val store = FakeStore(mutableMapOf(
            "a" to GmailBackupMode.ARCHIVE_APPEND_ONLY,
            "b" to GmailBackupMode.MIRROR
        ))
        val controller = GmailBackupModeController(store)
        controller.load("a")
        controller.openPolicyWizard(false)
        controller.choosePendingMode(GmailBackupMode.MIRROR)
        controller.confirmPolicyChange(false)

        assertEquals(GmailBackupMode.MIRROR, store.values["a"])
        assertEquals(GmailBackupMode.MIRROR, store.values["b"])
        assertEquals(1, store.writeCount)
    }

    @Test fun `stale save cannot replace newly selected profile state`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val store = object : GmailBackupModeStore {
            override suspend fun getBackupMode(profileId: String) =
                if (profileId == "old") GmailBackupMode.ARCHIVE_APPEND_ONLY else GmailBackupMode.MIRROR
            override suspend fun setBackupMode(profileId: String, mode: GmailBackupMode) { gate.await() }
        }
        val controller = GmailBackupModeController(store)
        controller.load("old")
        controller.openPolicyWizard(false)
        controller.choosePendingMode(GmailBackupMode.MIRROR)
        val save = async(start = CoroutineStart.UNDISPATCHED) {
            controller.confirmPolicyChange(false)
        }
        controller.load("new")
        gate.complete(Unit)
        save.await()

        assertEquals("new", controller.state.profileId)
        assertEquals(GmailBackupMode.MIRROR, controller.state.mode)
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

class GmailBackupPolicyMetadataTest {
    @Test fun `policy change stores previous current and timestamp without touching other settings`() {
        val original = AccountSettingsEntity(
            profileId = "p",
            backupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY.name,
            backupLabel = "Custom",
            includeContactNames = false
        )

        val changed = GmailBackupPolicyMetadata.changed(
            original,
            GmailBackupMode.MIRROR,
            changedAt = 1234L
        )

        assertEquals(GmailBackupMode.MIRROR.name, changed.backupMode)
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY.name, changed.previousPolicy)
        assertEquals(1234L, changed.policyChangedAt)
        assertEquals("Custom", changed.backupLabel)
        assertFalse(changed.includeContactNames)
    }

    @Test fun `same policy is a metadata no-op`() {
        val original = AccountSettingsEntity(profileId = "p")
        assertEquals(
            original,
            GmailBackupPolicyMetadata.changed(
                original,
                GmailBackupMode.ARCHIVE_APPEND_ONLY,
                changedAt = 99L
            )
        )
    }
}
