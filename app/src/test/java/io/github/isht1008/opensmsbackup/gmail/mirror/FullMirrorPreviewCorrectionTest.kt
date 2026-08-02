package io.github.isht1008.opensmsbackup.gmail.mirror

import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.database.MirrorPreviewLocalItemEntity
import io.github.isht1008.opensmsbackup.database.MirrorPreviewRemoteItemEntity
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import io.github.isht1008.opensmsbackup.gmail.backup.GmailMetadataBatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullMirrorPreviewCorrectionTest {
    private val binding = FullMirrorBinding(
        "scan", "profile-b", "mirror@example.test", "device-b", "label-b", "MIRROR"
    )
    private val local = FullMirrorLocalScalar(7, "snapshot", "local", 3)
    private val key = MirrorConversationIdentity.key(
        binding.profileId, binding.accountIdentity, binding.deviceId, local.androidThreadId
    )
    private val remote = MirrorPreviewRemoteItemEntity(
        scanId = "scan",
        gmailMessageId = "gmail-b",
        seenGeneration = 1,
        itemState = "METADATA",
        conversationKey = key,
        androidThreadId = 7,
        snapshotHash = "snapshot",
        identityVersion = MirrorConversationIdentity.WIRE_NAME,
        formatVersion = "3",
        accountBindingValid = true,
        deviceBindingValid = true,
        labelBindingValid = true,
        identityCurrent = true
    )
    private val cache = ConversationSnapshotEntity(
        profileId = "profile-b",
        accountId = "mirror@example.test",
        accountEmail = "mirror@example.test",
        androidThreadId = 7,
        address = "fixture",
        messageCount = 3,
        snapshotHash = "snapshot",
        gmailMessageId = "gmail-b",
        firstMessageDate = 1,
        lastMessageDate = 2
    )

    @Test fun exactTrustedCachedUnchangedSnapshotSkipsFullRead() {
        assertTrue(MirrorPreviewCacheFastPath.trustUnchanged(remote, binding, local, cache, 1))
    }

    @Test fun cacheValidatedScalarPreservesPlannerConservationAndProof() {
        val owned = OwnedRemoteSnapshot(
            key,
            "gmail-b",
            "snapshot",
            7,
            ownershipValid = true,
            readable = true,
            cacheMatches = true,
            identityCurrent = true,
            ownershipConversationKeyHeader = key,
            identityVersionHeader = MirrorConversationIdentity.WIRE_NAME,
            formatVersionHeader = "3"
        )
        val preview = FullMirrorScalarPreviewPlanner.create(
            binding.copy(runId = "run"),
            listOf(local),
            sourceMessages = 3,
            localComplete = true,
            localCountConsistent = true,
            localFailure = FullMirrorFailureCategory.NONE,
            remotes = listOf(owned),
            foreignIgnored = 0,
            now = 1
        )
        assertEquals(1, preview.count(FullMirrorAction.UNCHANGED))
        assertEquals(1, preview.localClassifiedCount)
        assertEquals(1, preview.remoteClassifiedCount)
        assertTrue(preview.executionAllowed)
        val changed = FullMirrorScalarPreviewPlanner.create(
            binding.copy(runId = "changed-run"),
            listOf(local.copy(snapshotHash = "changed")),
            3,
            true,
            true,
            FullMirrorFailureCategory.NONE,
            listOf(owned),
            0,
            1
        )
        assertEquals(1, changed.count(FullMirrorAction.REPLACE_CHANGED))
        assertTrue(changed.items.single().oldTargetProof?.isCompleteFor(
            changed.binding,
            changed.items.single()
        ) == true)
    }

    @Test fun everyImmutableMismatchDisablesCacheFastPath() {
        val mismatches = listOf(
            remote.copy(gmailMessageId = "different"),
            remote.copy(androidThreadId = 8),
            remote.copy(snapshotHash = "different"),
            remote.copy(conversationKey = "different"),
            remote.copy(identityVersion = "3"),
            remote.copy(formatVersion = "2"),
            remote.copy(accountBindingValid = false),
            remote.copy(deviceBindingValid = false),
            remote.copy(labelBindingValid = false)
        )
        mismatches.forEach {
            assertFalse(MirrorPreviewCacheFastPath.trustUnchanged(it, binding, local, cache, 1))
        }
        assertFalse(MirrorPreviewCacheFastPath.trustUnchanged(remote, binding, local, cache, 2))
        assertFalse(MirrorPreviewCacheFastPath.trustUnchanged(remote, binding, null, cache, 1))
        assertFalse(MirrorPreviewCacheFastPath.trustUnchanged(
            remote, binding, local.copy(snapshotHash = "changed"), cache, 1
        ))
    }

    @Test fun boundedMetadataSchedulingNeverQueuesAWholePage() {
        val batches = GmailMetadataBatching.batches((1..500).toList(), 4)
        assertEquals(125, batches.size)
        assertTrue(batches.all { it.size <= 4 })
        assertEquals((1..500).toList(), batches.flatten())
        val large = GmailMetadataBatching.batches((1..100_000).toList(), 4)
        assertEquals(25_000, large.size)
        assertTrue(large.all { it.size <= 4 })
        assertEquals(1, FullMirrorPreviewScanService.MAX_FULL_SNAPSHOT_CONCURRENCY)
    }

    @Test fun networkCircuitPausesAndHonorsRetryAfter() {
        assertTrue(MirrorPreviewNetworkCircuit.shouldPause(1))
        assertEquals(30_000L, MirrorPreviewNetworkCircuit.retryDelay(0, null))
        assertEquals(60_000L, MirrorPreviewNetworkCircuit.retryDelay(1, null))
        assertEquals(1_800_000L, MirrorPreviewNetworkCircuit.retryDelay(0, 1_800_000L))
        assertFalse(MirrorPreviewNetworkCircuit.mayAttempt(40_000L, 39_999L))
        assertTrue(MirrorPreviewNetworkCircuit.mayAttempt(40_000L, 40_000L))
        assertEquals(
            50_000L,
            MirrorPreviewNetworkCircuit.retryDeadline(10_000L, Long.MAX_VALUE, 50_000L)
        )
    }

    @Test fun generationRelistPromotesCompletedRowsWithoutRepeatingFullRead() {
        val completed = remote.copy(
            itemState = "VALIDATED",
            seenGeneration = 1,
            ownershipValid = true,
            readable = true
        )
        val selection = MirrorPreviewGenerationResume.select(
            pageIds = listOf(completed.gmailMessageId, "new-id", completed.gmailMessageId),
            currentGenerationIds = emptySet(),
            persistedItems = listOf(completed),
            generation = 2
        )
        assertEquals(listOf("new-id"), selection.metadataIds)
        assertEquals(1, selection.promoted.size)
        assertEquals(2, selection.promoted.single().seenGeneration)
        assertEquals("VALIDATED", selection.promoted.single().itemState)
        assertTrue(selection.promoted.single().ownershipValid)
    }

    @Test fun previewWorkNamesAndProfileTagsDoNotExposeImmutableIdentifiers() {
        val name = FullMirrorPreviewWorkContract.uniqueWorkName("profile-private", "device-private")
        val tag = FullMirrorPreviewWorkContract.profileTag("profile-private")
        assertFalse(name.contains("profile-private"))
        assertFalse(name.contains("device-private"))
        assertFalse(tag.contains("profile-private"))
        assertEquals(name, FullMirrorPreviewWorkContract.uniqueWorkName("profile-private", "device-private"))
        assertFalse(name == FullMirrorPreviewWorkContract.uniqueWorkName("profile-private", "other-device"))
    }

    @Test fun workDataAndProgressContainScalarValuesOnly() {
        val input = FullMirrorPreviewWorkInput("scan", "profile-b", "device-b", 10)
        assertEquals(input, FullMirrorPreviewWorkContract.readInput(
            FullMirrorPreviewWorkContract.inputData(input)
        ))
        val progress = FullMirrorPreviewProgress(
            "scan", FullMirrorPreviewStage.WAITING_NETWORK, 10, 10, 20, 18,
            2, 5, 12, 1, 30_000, 90_000
        )
        assertEquals(progress, FullMirrorPreviewWorkContract.readProgress(
            FullMirrorPreviewWorkContract.progressData(progress)
        ))
        assertTrue(FullMirrorPreviewProgressText.details(progress).contains("Waiting for network"))
        assertFalse(FullMirrorPreviewProgressText.details(progress).contains("@"))
        FullMirrorPreviewStage.entries.forEach { stage ->
            val title = FullMirrorPreviewProgressText.title(stage)
            assertTrue(title.isNotBlank())
            assertFalse(title.contains("Creating local backup"))
        }
    }

    @Test fun scanEntitiesPersistNoMessageContentOrAccountAddress() {
        val forbidden = setOf(
            "body", "smsbody", "gmailbody", "address", "contactname", "accountemail",
            "oauthtoken", "authorizationcode", "credential"
        )
        val fields = listOf(
            MirrorPreviewScanEntity::class,
            MirrorPreviewLocalItemEntity::class,
            MirrorPreviewRemoteItemEntity::class
        ).flatMap { type -> type.java.declaredFields.map { it.name.lowercase() } }
        forbidden.forEach { value ->
            assertFalse("Scan schema leaked $value", value in fields)
        }
    }

    @Test fun productionUiUsesWorkerAndPreservesLocalBackupLabel() {
        val viewModel = File("src/main/java/io/github/isht1008/opensmsbackup/viewmodel/HomeViewModel.kt").readText()
        val home = File("src/main/java/io/github/isht1008/opensmsbackup/ui/screen/HomeScreen.kt").readText()
        val scanner = File("src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorPreviewScanService.kt").readText()
        assertFalse(viewModel.contains("FullMirrorPreviewService(context).createPreview"))
        assertTrue(viewModel.contains("fullMirrorPreviewCoordinator.enqueue"))
        assertTrue(home.contains("FullMirrorPreviewProgressText.details"))
        assertTrue(home.contains("else -> \"Creating local backup...\""))
        assertFalse(scanner.contains("messages().insert"))
        assertFalse(scanner.contains("messages().trash"))
        assertFalse(scanner.contains("messages().delete"))
        assertFalse(scanner.contains("getSelectedAccountProfile"))
        assertTrue(scanner.contains("MirrorPreviewGenerationResume.select"))
        assertTrue(scanner.contains("if (!scan.localScanComplete)"))
    }

    @Test fun partialScanIdentifierCannotBeConfirmed() {
        val coordinator = File(
            "src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorCoordinator.kt"
        ).readText()
        assertTrue(coordinator.contains("mirrorReconciliationDao().findRun"))
        assertFalse(coordinator.contains("mirrorPreviewScanDao"))
        val output = FullMirrorPreviewWorkResult(
            "scan-only", null, FullMirrorPreviewScanState.WAITING_NETWORK
        )
        assertNull(FullMirrorPreviewWorkContract.readResult(
            FullMirrorPreviewWorkContract.outputData(output)
        )?.runId)
    }

    @Test fun durabilityAndAtomicPublicationGuardsArePresent() {
        val coordinator = File(
            "src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorPreviewCoordinator.kt"
        ).readText()
        val worker = File(
            "src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorPreviewWorker.kt"
        ).readText()
        val scanner = File(
            "src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorPreviewScanService.kt"
        ).readText()
        val dao = File(
            "src/main/java/io/github/isht1008/opensmsbackup/database/MirrorPreviewScanDao.kt"
        ).readText()
        val requestFactory = File(
            "src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorPreviewRequestFactory.kt"
        ).readText()
        val recovery = File(
            "src/main/java/io/github/isht1008/opensmsbackup/gmail/mirror/FullMirrorPreviewRecovery.kt"
        ).readText()
        assertTrue(coordinator.contains("activeUnique(uniqueName)"))
        assertTrue(coordinator.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(requestFactory.contains("NetworkType.CONNECTED"))
        assertTrue(worker.contains("Result.retry()"))
        assertTrue(recovery.contains("isExplicitCancellation"))
        assertTrue(scanner.contains("findRemoteItemsByState(scan.scanId, \"REQUIRED\")"))
        assertTrue(scanner.contains("return ReadResult.Terminal(handleGmailFailure"))
        assertTrue(dao.contains("@Transaction"))
        assertTrue(dao.contains("suspend fun publishExecutablePreview"))
        assertTrue(dao.contains("run.runId != scanId"))
        assertTrue(dao.contains("scan.expiresAt >= now"))
        assertTrue(dao.indexOf("insertReconciliationRun(run)") <
            dao.indexOf("lifecycleState = \"PUBLISHED\""))
        assertTrue(scanner.indexOf("settings.backupMode != GmailBackupMode.MIRROR.name") <
            scanner.indexOf("GmailApiClient(context).createService(profile)"))
    }
}
