package io.github.isht1008.opensmsbackup.gmail.mirror

import android.content.Context
import android.util.Log
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.database.MirrorPreviewLocalItemEntity
import io.github.isht1008.opensmsbackup.database.MirrorPreviewRemoteItemEntity
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import io.github.isht1008.opensmsbackup.device.DeviceProfileStore
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.backup.ConversationSnapshotHashGenerator
import io.github.isht1008.opensmsbackup.gmail.backup.DeviceSnapshotOwnership
import io.github.isht1008.opensmsbackup.gmail.backup.GmailArchivedConversationReader
import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshotBuilder
import io.github.isht1008.opensmsbackup.gmail.error.GmailFailure
import io.github.isht1008.opensmsbackup.gmail.error.GmailFailureCategory
import io.github.isht1008.opensmsbackup.gmail.error.GmailOperationException
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale
import java.util.UUID

sealed interface FullMirrorPreviewScanOutcome {
    data class Published(val runId: String) : FullMirrorPreviewScanOutcome
    data class Retry(val category: String) : FullMirrorPreviewScanOutcome
    data class Failed(val category: String) : FullMirrorPreviewScanOutcome
    data object Expired : FullMirrorPreviewScanOutcome
}

class FullMirrorPreviewScanService(private val context: Context) {
    private val database = DatabaseProvider.getDatabase(context)
    private val scanDao = database.mirrorPreviewScanDao()

    suspend fun run(
        scanId: String,
        onProgress: suspend (FullMirrorPreviewProgress) -> Unit = {}
    ): FullMirrorPreviewScanOutcome {
        var scan = requireNotNull(scanDao.findScan(scanId))
        val now = System.currentTimeMillis()
        if (scan.expiresAt <= now) {
            scanDao.updateScan(scan.copy(
                lifecycleState = FullMirrorPreviewScanState.EXPIRED.name,
                stage = FullMirrorPreviewStage.PAUSED.name,
                updatedAt = now
            ))
            return FullMirrorPreviewScanOutcome.Expired
        }
        if (!MirrorPreviewNetworkCircuit.mayAttempt(scan.retryAt, now)) {
            return FullMirrorPreviewScanOutcome.Retry(
                scan.lastErrorCategory ?: GmailFailureCategory.NETWORK.name
            )
        }
        val profile = database.accountProfileDao().findProfileById(scan.profileId)
            ?: return fail(scan, FullMirrorFailureCategory.PROFILE_MISMATCH.name)
        val account = profile.accountEmail.trim().lowercase(Locale.ROOT)
        if (
            profile.connectionState != io.github.isht1008.opensmsbackup.database.AccountProfileEntity.CONNECTION_STATE_CONNECTED ||
            MirrorPreviewAccountBinding.fingerprint(profile.profileId, account) != scan.accountFingerprint
        ) return fail(scan, FullMirrorFailureCategory.ACCOUNT_MISMATCH.name)
        val settings = database.accountProfileDao().findSettings(scan.profileId)
            ?: return fail(scan, FullMirrorFailureCategory.PROFILE_MISMATCH.name)
        if (settings.backupMode != GmailBackupMode.MIRROR.name ||
            scan.expectedPolicy != GmailBackupMode.MIRROR.name
        ) return fail(scan, FullMirrorFailureCategory.POLICY_CHANGED.name)
        val deviceStore = DeviceProfileStore.create(context)
        val device = deviceStore.getOrCreate()
        if (device.deviceId != scan.deviceId ||
            deviceStore.getGmailDeviceLabelId(scan.profileId) != scan.deviceLabelId
        ) return fail(scan, FullMirrorFailureCategory.DEVICE_MISMATCH.name)

        val scanBinding = FullMirrorBinding(
            scanId,
            scan.profileId,
            account,
            scan.deviceId,
            scan.deviceLabelId,
            scan.expectedPolicy
        )
        scan = update(scan, FullMirrorPreviewScanState.RUNNING, FullMirrorPreviewStage.PREPARING)
        progress(scan, onProgress)

        if (!scan.localScanComplete) {
            scan = scanLocal(scan, scanBinding, device.defaultRegion, onProgress)
        }
        currentCoroutineContext().ensureActive()
        val localEntities = scanDao.findLocalItems(scanId)
        val locals = localEntities.map { it.toScalar() }
        if (FullMirrorScalarPreviewPlanner.fingerprintLocal(locals) != scan.localDatasetFingerprint) {
            return fail(scan, FullMirrorFailureCategory.LOCAL_CHANGED.name)
        }

        val reader = GmailArchivedConversationReader(
            GmailApiClient(context).createService(profile),
            scan.profileId
        )
        if (!scan.remoteDiscoveryComplete) {
            val discovery = discover(scan, scanBinding, reader, onProgress)
            if (discovery is DiscoveryResult.Terminal) return discovery.outcome
            scan = (discovery as DiscoveryResult.Complete).scan
        }
        currentCoroutineContext().ensureActive()
        scan = prepareRequiredReads(scan, scanBinding, locals)
        progress(scan, onProgress)
        val reads = readRequiredSnapshots(scan, scanBinding, reader, onProgress)
        if (reads is ReadResult.Terminal) return reads.outcome
        scan = (reads as ReadResult.Complete).scan

        val consistent = verifyRemoteGeneration(scan, reader)
        if (consistent is GenerationResult.Terminal) return consistent.outcome
        scan = consistent.scan
        if (!revalidateLocal(scan, device.defaultRegion)) {
            return fail(scan, FullMirrorFailureCategory.LOCAL_CHANGED.name)
        }
        val reboundProfile = database.accountProfileDao().findProfileById(scan.profileId)
            ?: return fail(scan, FullMirrorFailureCategory.PROFILE_MISMATCH.name)
        val reboundSettings = database.accountProfileDao().findSettings(scan.profileId)
            ?: return fail(scan, FullMirrorFailureCategory.PROFILE_MISMATCH.name)
        val reboundDevice = deviceStore.getOrCreate()
        if (
            MirrorPreviewAccountBinding.fingerprint(reboundProfile.profileId, reboundProfile.accountEmail) !=
                scan.accountFingerprint ||
            reboundSettings.backupMode != GmailBackupMode.MIRROR.name ||
            reboundDevice.deviceId != scan.deviceId ||
            deviceStore.getGmailDeviceLabelId(scan.profileId) != scan.deviceLabelId
        ) return fail(scan, FullMirrorFailureCategory.PROFILE_MISMATCH.name)

        scan = update(scan, FullMirrorPreviewScanState.FINALIZING, FullMirrorPreviewStage.FINALIZING)
        progress(scan, onProgress)
        val currentRemoteEntities = scanDao.findRemoteItems(scanId)
            .filter { it.seenGeneration == scan.remoteGeneration }
        val remoteEntities = currentRemoteEntities.filter { it.itemState != "IGNORED" }
        if (remoteEntities.any { it.itemState != "VALIDATED" }) {
            return fail(scan, FullMirrorFailureCategory.UNREADABLE.name)
        }
        val remotes = remoteEntities.map { it.toOwnedRemote() }
        val finalBinding = scanBinding.copy(runId = UUID.randomUUID().toString())
        val preview = FullMirrorScalarPreviewPlanner.create(
            binding = finalBinding,
            locals = locals,
            sourceMessages = scan.localMessageCount,
            localComplete = scan.localScanComplete,
            localCountConsistent = scan.localCountConsistent,
            localFailure = scan.localFailureCategory?.let(FullMirrorFailureCategory::valueOf)
                ?: FullMirrorFailureCategory.NONE,
            remotes = remotes,
            foreignIgnored = scan.foreignIgnored,
            now = System.currentTimeMillis(),
            discoveryReasons = currentRemoteEntities.mapNotNull { entity ->
                entity.reason?.let { runCatching { FullMirrorFailureCategory.valueOf(it) }.getOrNull() }
            }.groupingBy { it }.eachCount()
        )
        check(preview.localDatasetFingerprint == scan.localDatasetFingerprint)
        check(preview.remoteIndexFingerprint == FullMirrorPreviewPlanner.fingerprintRemote(remotes))
        scanDao.publishExecutablePreview(
            scanId,
            FullMirrorPreviewPersistence.runEntity(preview),
            FullMirrorPreviewPersistence.itemEntities(preview),
            System.currentTimeMillis()
        )
        scanDao.findScan(scanId)?.let {
            FullMirrorPreviewDiagnostics.terminal(it, "PUBLISHED", preview.binding.runId)
        }
        onProgress(progressValue(scan.copy(
            lifecycleState = FullMirrorPreviewScanState.PUBLISHED.name,
            stage = FullMirrorPreviewStage.READY.name
        )))
        Log.i(
            "OpenSMSBackup",
            "full_mirror_preview_scan stage=ready local=${preview.localConversations} " +
                "remote=${preview.remoteCandidates} cached=${scan.cachedUnchanged} " +
                "full_reads=${scan.fullReadsCompleted}"
        )
        return FullMirrorPreviewScanOutcome.Published(preview.binding.runId)
    }

    private suspend fun scanLocal(
        original: MirrorPreviewScanEntity,
        binding: FullMirrorBinding,
        defaultRegion: String,
        onProgress: suspend (FullMirrorPreviewProgress) -> Unit
    ): MirrorPreviewScanEntity {
        var scan = update(original, FullMirrorPreviewScanState.RUNNING, FullMirrorPreviewStage.READING_LOCAL_SMS)
        progress(scan, onProgress)
        val read = SmsRepository().getCompleteSmsMessages(context, scan.includeContactNames)
        currentCoroutineContext().ensureActive()
        scan = update(scan, FullMirrorPreviewScanState.RUNNING, FullMirrorPreviewStage.BUILDING_LOCAL_INDEX)
        val conversations = SmsConversationSnapshotBuilder().build(read.messages)
        val scalars = conversations.map { conversation ->
            FullMirrorLocalScalarFactory.create(
                binding.profileId,
                binding.accountIdentity,
                binding.deviceId,
                defaultRegion,
                conversation
            )
        }
        val entities = scalars.mapIndexed { ordinal, scalar ->
            val key = if (scalar.androidThreadId > 0L) {
                MirrorConversationIdentity.key(
                    binding.profileId,
                    binding.accountIdentity,
                    binding.deviceId,
                    scalar.androidThreadId
                )
            } else MirrorPreviewAccountBinding.digest("invalid-thread\u0000${scalar.localSourceHash}")
            MirrorPreviewLocalItemEntity(
                scan.scanId,
                ordinal,
                scalar.androidThreadId,
                key,
                scalar.snapshotHash,
                scalar.localSourceHash,
                scalar.messageCount,
                scalar.diagnosticReason?.name
            )
        }
        val failure = if (read.failureCategory == null) FullMirrorFailureCategory.NONE
            else FullMirrorFailureCategory.INCOMPLETE_LOCAL_SCAN
        val checkpoint = scan.copy(
            stage = FullMirrorPreviewStage.LISTING_GMAIL.name,
            updatedAt = System.currentTimeMillis(),
            localDatasetFingerprint = FullMirrorScalarPreviewPlanner.fingerprintLocal(scalars),
            localScanComplete = read.complete,
            localCountConsistent = read.providerCount == read.messages.size,
            localFailureCategory = failure.name,
            localMessageCount = read.messages.size,
            localConversationCount = scalars.size,
            localProcessed = scalars.size
        )
        scanDao.replaceLocalCheckpoint(checkpoint, entities)
        progress(checkpoint, onProgress)
        return checkpoint
    }

    private suspend fun discover(
        original: MirrorPreviewScanEntity,
        binding: FullMirrorBinding,
        reader: GmailArchivedConversationReader,
        onProgress: suspend (FullMirrorPreviewProgress) -> Unit
    ): DiscoveryResult {
        var scan = update(original, FullMirrorPreviewScanState.RUNNING, FullMirrorPreviewStage.LISTING_GMAIL)
        var token = scan.remotePageCursor
        try {
            do {
                currentCoroutineContext().ensureActive()
                val page = reader.listPage(binding.deviceLabelId, token)
                val pageIds = page.messageIds.filter(String::isNotBlank).distinct()
                val persisted = if (pageIds.isEmpty()) emptyList()
                    else scanDao.findRemoteItemsByIds(scan.scanId, pageIds)
                val known = persisted.asSequence()
                    .filter { it.seenGeneration == scan.remoteGeneration }
                    .map { it.gmailMessageId }
                    .toSet()
                val resume = MirrorPreviewGenerationResume.select(
                    pageIds,
                    known,
                    persisted,
                    scan.remoteGeneration
                )
                if (resume.promoted.isNotEmpty()) {
                    resume.promoted.chunked(500).forEach { promoted ->
                        scanDao.upsertRemoteItems(promoted)
                    }
                }
                val ids = resume.metadataIds
                for (batch in ids.chunked(METADATA_CHECKPOINT_BATCH_SIZE)) {
                    scan = update(scan, FullMirrorPreviewScanState.RUNNING, FullMirrorPreviewStage.CHECKING_METADATA)
                    progress(scan, onProgress)
                    val references = reader.readMetadataPage(batch)
                    if (references.size != batch.size ||
                        references.map { it.messageId }.toSet() != batch.toSet()
                    ) {
                        return DiscoveryResult.Terminal(restartGeneration(scan, "REMOTE_CHANGED"))
                    }
                    val decisions = references.map {
                        MirrorPreviewMetadataValidator.validate(
                            scan.scanId,
                            scan.remoteGeneration,
                            it,
                            binding
                        )
                    }
                    scanDao.upsertRemoteItems(decisions.map { it.item })
                    val aggregate = scanDao.remoteProgress(scan.scanId, scan.remoteGeneration)
                    if (aggregate.total > MAX_REMOTE_DISCOVERY_ITEMS) {
                        return DiscoveryResult.Terminal(
                            fail(scan, FullMirrorFailureCategory.LIMIT_EXCEEDED.name)
                        )
                    }
                    scan = clearRetry(scan).copy(
                        updatedAt = System.currentTimeMillis(),
                        remoteDiscovered = aggregate.total,
                        metadataChecked = aggregate.total,
                        foreignIgnored = aggregate.ignored
                    )
                    check(scanDao.updateScan(scan) == 1)
                    progress(scan, onProgress)
                }
                token = page.nextPageToken
                val aggregate = scanDao.remoteProgress(scan.scanId, scan.remoteGeneration)
                scan = clearRetry(scan).copy(
                    lifecycleState = FullMirrorPreviewScanState.RUNNING.name,
                    stage = FullMirrorPreviewStage.LISTING_GMAIL.name,
                    updatedAt = System.currentTimeMillis(),
                    remotePageCursor = token,
                    remoteDiscovered = aggregate.total,
                    metadataChecked = aggregate.total,
                    foreignIgnored = aggregate.ignored
                )
                check(scanDao.updateScan(scan) == 1)
                progress(scan, onProgress)
            } while (token != null)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: GmailOperationException) {
            if (error.failure.httpStatusCode == 400 && scan.remotePageCursor != null) {
                return DiscoveryResult.Terminal(restartGeneration(scan, "PAGE_CURSOR_INVALID"))
            }
            return DiscoveryResult.Terminal(handleGmailFailure(scan, error.failure, "metadata_or_listing"))
        }
        val ids = scanDao.findSeenMessageIds(scan.scanId, scan.remoteGeneration)
        scanDao.deleteOtherGenerations(scan.scanId, scan.remoteGeneration)
        val complete = scan.copy(
            lifecycleState = FullMirrorPreviewScanState.RUNNING.name,
            stage = FullMirrorPreviewStage.READING_REQUIRED_SNAPSHOTS.name,
            updatedAt = System.currentTimeMillis(),
            remotePageCursor = null,
            remoteDiscoveryComplete = true,
            remoteDiscoveryFingerprint = fingerprintIds(ids),
            remoteDiscovered = ids.size,
            metadataChecked = ids.size
        )
        check(scanDao.updateScan(complete) == 1)
        return DiscoveryResult.Complete(complete)
    }

    private suspend fun prepareRequiredReads(
        original: MirrorPreviewScanEntity,
        binding: FullMirrorBinding,
        locals: List<FullMirrorLocalScalar>
    ): MirrorPreviewScanEntity {
        val all = scanDao.findRemoteItems(original.scanId)
            .filter { it.seenGeneration == original.remoteGeneration }
        val localGroups = locals.groupBy { it.androidThreadId }
        val cache = database.conversationSnapshotDao()
            .findAllForProfile(binding.profileId, binding.accountIdentity)
            .associateBy { it.androidThreadId }
        val duplicateCounts = all.filter { it.itemState != "IGNORED" }
            .groupingBy { it.conversationKey }.eachCount()
        val updated = all.filter { it.itemState == "METADATA" }.map { item ->
            val threadId = item.androidThreadId
            val local = threadId?.let { localGroups[it]?.singleOrNull() }
            val cached = threadId?.let(cache::get)
            if (MirrorPreviewCacheFastPath.trustUnchanged(
                    item,
                    binding,
                    local,
                    cached,
                    duplicateCounts[item.conversationKey] ?: 0
                )
            ) item.copy(
                itemState = "VALIDATED",
                ownershipValid = true,
                readable = true,
                cacheMatches = true,
                identityCurrent = true,
                reason = null
            ) else item.copy(itemState = "REQUIRED")
        }
        updated.chunked(500).forEach { scanDao.upsertRemoteItems(it) }
        val aggregate = scanDao.remoteProgress(original.scanId, original.remoteGeneration)
        val scan = original.copy(
            stage = FullMirrorPreviewStage.READING_REQUIRED_SNAPSHOTS.name,
            updatedAt = System.currentTimeMillis(),
            remoteDiscovered = aggregate.total,
            metadataChecked = aggregate.total,
            foreignIgnored = aggregate.ignored,
            fullReadsRequired = aggregate.required + aggregate.validatedFull,
            fullReadsCompleted = aggregate.validatedFull,
            cachedUnchanged = aggregate.cachedUnchanged
        )
        check(scanDao.updateScan(scan) == 1)
        return scan
    }

    private suspend fun readRequiredSnapshots(
        original: MirrorPreviewScanEntity,
        binding: FullMirrorBinding,
        reader: GmailArchivedConversationReader,
        onProgress: suspend (FullMirrorPreviewProgress) -> Unit
    ): ReadResult {
        var scan = original
        val cache = database.conversationSnapshotDao()
            .findAllForProfile(binding.profileId, binding.accountIdentity)
            .associateBy { it.androidThreadId }
        for (item in scanDao.findRemoteItemsByState(scan.scanId, "REQUIRED")
            .filter { it.seenGeneration == scan.remoteGeneration }) {
            currentCoroutineContext().ensureActive()
            val result = reader.read(item.gmailMessageId)
            val error = result.exceptionOrNull()
            if (error != null) {
                when (error) {
                    is CancellationException -> throw error
                    is GmailOperationException ->
                        return ReadResult.Terminal(handleGmailFailure(scan, error.failure, "full_snapshot"))
                    is io.github.isht1008.opensmsbackup.gmail.backup.ArchiveMessageNotFoundException ->
                        return ReadResult.Terminal(restartGeneration(scan, "REMOTE_CHANGED"))
                    is io.github.isht1008.opensmsbackup.gmail.backup.InvalidArchiveSnapshotException -> {
                        scanDao.upsertRemoteItems(listOf(item.copy(
                            itemState = "VALIDATED",
                            readable = false,
                            ownershipValid = false,
                            reason = FullMirrorFailureCategory.UNREADABLE.name,
                            fullReadAttempts = item.fullReadAttempts + 1
                        )))
                    }
                    else -> return ReadResult.Terminal(fail(scan, "UNKNOWN_READ_FAILURE"))
                }
            } else {
                val document = result.getOrThrow()
                val threadId = document.conversation.threadId
                val cached = cache[threadId]
                val cacheMatches = cached?.profileId == binding.profileId &&
                    cached.accountId.equals(binding.accountIdentity, true) &&
                    cached.gmailMessageId == item.gmailMessageId
                val currentIdentity = item.identityVersion == MirrorConversationIdentity.WIRE_NAME
                val currentOwned = currentIdentity && DeviceSnapshotOwnership.matchesMirrorThread(
                    document,
                    binding.profileId,
                    binding.accountIdentity,
                    binding.deviceId,
                    binding.deviceLabelId,
                    threadId
                )
                val legacyOwned = !currentIdentity && cacheMatches &&
                    DeviceSnapshotOwnership.matchesV2(
                        document,
                        binding.accountIdentity,
                        binding.deviceId,
                        binding.deviceLabelId,
                        document.conversation
                    )
                val reason = when {
                    threadId <= 0L -> FullMirrorFailureCategory.INVALID_THREAD_ID
                    currentIdentity && !currentOwned -> FullMirrorFailureCategory.OWNERSHIP
                    !currentIdentity && !cacheMatches -> FullMirrorFailureCategory.CACHED_ROOM_MISMATCH
                    !currentIdentity && !legacyOwned -> FullMirrorFailureCategory.AMBIGUOUS_LEGACY_IDENTITY
                    else -> FullMirrorFailureCategory.NONE
                }
                val key = if (threadId > 0L) MirrorConversationIdentity.key(
                    binding.profileId,
                    binding.accountIdentity,
                    binding.deviceId,
                    threadId
                ) else item.conversationKey
                scanDao.upsertRemoteItems(listOf(item.copy(
                    itemState = "VALIDATED",
                    conversationKey = key,
                    androidThreadId = threadId,
                    snapshotHash = ConversationSnapshotHashGenerator.generate(document.conversation),
                    ownershipValid = currentOwned || legacyOwned,
                    readable = true,
                    cacheMatches = cacheMatches,
                    identityCurrent = currentIdentity,
                    reason = reason.takeUnless { it == FullMirrorFailureCategory.NONE }?.name,
                    fullReadAttempts = item.fullReadAttempts + 1
                )))
            }
            val aggregate = scanDao.remoteProgress(scan.scanId, scan.remoteGeneration)
            scan = clearRetry(scan).copy(
                lifecycleState = FullMirrorPreviewScanState.RUNNING.name,
                stage = FullMirrorPreviewStage.READING_REQUIRED_SNAPSHOTS.name,
                updatedAt = System.currentTimeMillis(),
                fullReadsRequired = aggregate.required + aggregate.validatedFull,
                fullReadsCompleted = aggregate.validatedFull,
                cachedUnchanged = aggregate.cachedUnchanged
            )
            check(scanDao.updateScan(scan) == 1)
            progress(scan, onProgress)
        }
        return ReadResult.Complete(scan)
    }

    private suspend fun verifyRemoteGeneration(
        original: MirrorPreviewScanEntity,
        reader: GmailArchivedConversationReader
    ): GenerationResult {
        val ids = mutableSetOf<String>()
        var token: String? = null
        return try {
            do {
                currentCoroutineContext().ensureActive()
                val page = reader.listPage(original.deviceLabelId, token)
                ids += page.messageIds.filter(String::isNotBlank)
                token = page.nextPageToken
            } while (token != null)
            if (fingerprintIds(ids) != original.remoteDiscoveryFingerprint) {
                GenerationResult.Terminal(restartGeneration(original, "REMOTE_CHANGED"))
            } else {
                GenerationResult.Complete(original)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: GmailOperationException) {
            if (error.failure.httpStatusCode == 400 && token != null) {
                GenerationResult.Terminal(restartGeneration(original, "PAGE_CURSOR_INVALID"))
            } else {
                GenerationResult.Terminal(handleGmailFailure(original, error.failure, "consistency_listing"))
            }
        }
    }

    private suspend fun revalidateLocal(
        scan: MirrorPreviewScanEntity,
        defaultRegion: String
    ): Boolean {
        val read = SmsRepository().getCompleteSmsMessages(context, scan.includeContactNames)
        if (!read.complete || read.providerCount != read.messages.size) return false
        val profile = database.accountProfileDao().findProfileById(scan.profileId) ?: return false
        val conversations = SmsConversationSnapshotBuilder().build(read.messages)
        val scalars = conversations.map {
            FullMirrorLocalScalarFactory.create(
                scan.profileId,
                profile.accountEmail,
                scan.deviceId,
                defaultRegion,
                it
            )
        }
        return FullMirrorScalarPreviewPlanner.fingerprintLocal(scalars) ==
            scan.localDatasetFingerprint
    }

    private suspend fun handleGmailFailure(
        scan: MirrorPreviewScanEntity,
        failure: GmailFailure,
        operation: String
    ): FullMirrorPreviewScanOutcome {
        val transient = failure.category in setOf(
            GmailFailureCategory.NETWORK,
            GmailFailureCategory.SERVER,
            GmailFailureCategory.RATE_LIMIT
        )
        if (!transient) {
            return fail(scan, failure.category.name)
        }
        check(MirrorPreviewNetworkCircuit.shouldPause(1))
        val delay = MirrorPreviewNetworkCircuit.retryDelay(
            scan.retryCount,
            failure.retryAfterMillis
        )
        val paused = scan.copy(
            lifecycleState = FullMirrorPreviewScanState.WAITING_NETWORK.name,
            stage = FullMirrorPreviewStage.WAITING_NETWORK.name,
            updatedAt = System.currentTimeMillis(),
            retryCount = scan.retryCount + 1,
            retryAttempt = scan.retryCount + 1,
            retryAt = MirrorPreviewNetworkCircuit.retryDeadline(
                System.currentTimeMillis(),
                delay,
                scan.expiresAt
            ),
            lastErrorCategory = failure.category.name,
            lastErrorSubtype = failure.safeExceptionSubtype.name
        )
        check(scanDao.updateScan(paused) == 1)
        Log.w(
            "OpenSMSBackup",
            "full_mirror_preview_scan stage=waiting_network operation=$operation " +
                "category=${failure.category} subtype=${failure.safeExceptionSubtype} " +
                "attempt=${paused.retryAttempt} retry_delay_ms=$delay " +
                "metadata=${paused.metadataChecked}/${paused.remoteDiscovered} " +
                "full_reads=${paused.fullReadsCompleted}/${paused.fullReadsRequired}"
        )
        return FullMirrorPreviewScanOutcome.Retry(failure.category.name)
    }

    private suspend fun restartGeneration(
        scan: MirrorPreviewScanEntity,
        category: String
    ): FullMirrorPreviewScanOutcome {
        val reset = scan.copy(
            lifecycleState = FullMirrorPreviewScanState.PAUSED.name,
            stage = FullMirrorPreviewStage.PAUSED.name,
            updatedAt = System.currentTimeMillis(),
            remoteGeneration = scan.remoteGeneration + 1,
            remotePageCursor = null,
            remoteDiscoveryComplete = false,
            remoteDiscoveryFingerprint = null,
            remoteDiscovered = 0,
            metadataChecked = 0,
            fullReadsRequired = 0,
            fullReadsCompleted = 0,
            cachedUnchanged = 0,
            foreignIgnored = 0,
            retryCount = scan.retryCount + 1,
            retryAttempt = scan.retryAttempt + 1,
            retryAt = MirrorPreviewNetworkCircuit.retryDeadline(
                System.currentTimeMillis(),
                MirrorPreviewNetworkCircuit.BASE_DELAY_MILLIS,
                scan.expiresAt
            ),
            lastErrorCategory = category,
            lastErrorSubtype = "REMOTE_GENERATION"
        )
        check(scanDao.updateScan(reset) == 1)
        Log.w(
            "OpenSMSBackup",
            "full_mirror_preview_scan stage=paused operation=relist " +
                "category=$category generation_restart=true"
        )
        return FullMirrorPreviewScanOutcome.Retry(category)
    }

    private suspend fun fail(
        scan: MirrorPreviewScanEntity,
        category: String
    ): FullMirrorPreviewScanOutcome.Failed {
        scanDao.updateScan(scan.copy(
            lifecycleState = FullMirrorPreviewScanState.FAILED.name,
            stage = FullMirrorPreviewStage.PAUSED.name,
            updatedAt = System.currentTimeMillis(),
            lastErrorCategory = category
        ))
        Log.w(
            "OpenSMSBackup",
            "full_mirror_preview_scan stage=paused category=$category executable_plan=false"
        )
        return FullMirrorPreviewScanOutcome.Failed(category)
    }

    private suspend fun update(
        scan: MirrorPreviewScanEntity,
        state: FullMirrorPreviewScanState,
        stage: FullMirrorPreviewStage
    ): MirrorPreviewScanEntity {
        val updated = scan.copy(
            lifecycleState = state.name,
            stage = stage.name,
            updatedAt = System.currentTimeMillis()
        )
        check(scanDao.updateScan(updated) == 1)
        if (scan.stage != updated.stage || scan.lifecycleState != updated.lifecycleState) {
            FullMirrorPreviewDiagnostics.stage(updated)
        } else {
            FullMirrorPreviewDiagnostics.progress(updated)
        }
        return updated
    }

    private suspend fun progress(
        scan: MirrorPreviewScanEntity,
        callback: suspend (FullMirrorPreviewProgress) -> Unit
    ) {
        FullMirrorPreviewDiagnostics.progress(scan)
        callback(progressValue(scan))
    }

    private fun clearRetry(scan: MirrorPreviewScanEntity) = scan.copy(
        retryCount = 0,
        retryAttempt = 0,
        retryAt = null,
        lastErrorCategory = null,
        lastErrorSubtype = null
    )

    private fun progressValue(scan: MirrorPreviewScanEntity) = FullMirrorPreviewProgress(
        scanId = scan.scanId,
        stage = runCatching { FullMirrorPreviewStage.valueOf(scan.stage) }
            .getOrDefault(FullMirrorPreviewStage.PREPARING),
        localProcessed = scan.localProcessed,
        localTotal = scan.localConversationCount,
        remoteDiscovered = scan.remoteDiscovered,
        metadataChecked = scan.metadataChecked,
        fullReadsCompleted = scan.fullReadsCompleted,
        fullReadsRequired = scan.fullReadsRequired,
        cachedUnchanged = scan.cachedUnchanged,
        retryAttempt = scan.retryAttempt,
        retryDelayMillis = scan.retryAt?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) },
        elapsedMillis = (System.currentTimeMillis() - scan.createdAt).coerceAtLeast(0L)
    )

    private fun MirrorPreviewLocalItemEntity.toScalar() = FullMirrorLocalScalar(
        androidThreadId,
        snapshotHash,
        localSourceHash,
        messageCount,
        diagnosticReason?.let {
            runCatching { FullMirrorFailureCategory.valueOf(it) }.getOrNull()
        }
    )

    private fun MirrorPreviewRemoteItemEntity.toOwnedRemote() = OwnedRemoteSnapshot(
        conversationKey = conversationKey,
        messageId = gmailMessageId,
        snapshotHash = snapshotHash,
        androidThreadId = androidThreadId,
        ownershipValid = ownershipValid,
        readable = readable,
        cacheMatches = cacheMatches,
        identityCurrent = identityCurrent,
        reason = reason?.let {
            runCatching { FullMirrorFailureCategory.valueOf(it) }.getOrNull()
        } ?: FullMirrorFailureCategory.NONE,
        ownershipConversationKeyHeader = conversationKey,
        identityVersionHeader = identityVersion,
        formatVersionHeader = formatVersion
    )

    private fun fingerprintIds(ids: Collection<String>) =
        MirrorPreviewAccountBinding.digest(ids.sorted().joinToString("\u0000"))

    private sealed interface DiscoveryResult {
        data class Complete(val scan: MirrorPreviewScanEntity) : DiscoveryResult
        data class Terminal(val outcome: FullMirrorPreviewScanOutcome) : DiscoveryResult
    }

    private sealed interface ReadResult {
        data class Complete(val scan: MirrorPreviewScanEntity) : ReadResult
        data class Terminal(val outcome: FullMirrorPreviewScanOutcome) : ReadResult
    }

    private sealed interface GenerationResult {
        val scan: MirrorPreviewScanEntity
        data class Complete(override val scan: MirrorPreviewScanEntity) : GenerationResult
        data class Terminal(val outcome: FullMirrorPreviewScanOutcome) : GenerationResult {
            override val scan: MirrorPreviewScanEntity
                get() = error("Terminal generation has no resumable in-memory state.")
        }
    }

    companion object {
        const val METADATA_CHECKPOINT_BATCH_SIZE = 16
        const val MAX_FULL_SNAPSHOT_CONCURRENCY = 1
        const val TRANSIENT_TERMINAL_FAILURES_TO_PAUSE =
            MirrorPreviewNetworkCircuit.TERMINAL_FAILURES_TO_PAUSE
        const val MAX_REMOTE_DISCOVERY_ITEMS = 100_000
    }
}
