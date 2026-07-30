package io.github.isht1008.opensmsbackup.gmail.mirror

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface FullMirrorMutationGateway {
    suspend fun validateBinding(binding: FullMirrorBinding): Boolean
    suspend fun currentLocalSourceHash(item: FullMirrorPreviewItem): String?
    suspend fun inspectOldTarget(binding: FullMirrorBinding, item: FullMirrorPreviewItem): FullMirrorOldTargetStatus
    suspend fun validatePersistedReplacement(binding: FullMirrorBinding, item: FullMirrorPreviewItem, resultingMessageId: String): Boolean
    suspend fun upload(item: FullMirrorPreviewItem): String
    suspend fun persistUploaded(item: FullMirrorPreviewItem, resultingMessageId: String)
    suspend fun trashOwned(messageId: String)
    suspend fun persistRemoteOnlyRemoval(item: FullMirrorPreviewItem)
    suspend fun recoverCache(item: FullMirrorPreviewItem)
}

interface FullMirrorExecutionJournal {
    suspend fun state(itemId: String): FullMirrorItemState
    suspend fun mark(itemId: String, state: FullMirrorItemState, resultingMessageId: String? = null, warning: FullMirrorFailureCategory? = null)
}

data class FullMirrorExecutionSummary(
    val completed: Int,
    val warnings: Int,
    val failed: Int,
    val remaining: Int,
    val newUploaded: Int,
    val changedReplaced: Int,
    val previousTrashed: Int,
    val remoteOnlyTrashed: Int
)

class FullMirrorExecutor(
    private val gateway: FullMirrorMutationGateway,
    private val journal: FullMirrorExecutionJournal,
    private val onProgress: suspend (FullMirrorExecutionSummary) -> Unit = {}
) {
    suspend fun execute(preview: FullMirrorPreview): FullMirrorExecutionSummary {
        require(preview.executionAllowed) { "Full Mirror preview is not executable." }
        check(gateway.validateBinding(preview.binding)) { "Mirror profile/account/device binding changed." }
        var completed = 0; var warnings = 0; var failed = 0
        var newUploaded = 0; var replaced = 0; var previousTrashed = 0; var remoteOnlyTrashed = 0
        for (item in preview.items) {
            currentCoroutineContext().ensureActive()
            check(gateway.validateBinding(preview.binding)) { "Mirror binding changed during execution." }
            var state = journal.state(item.itemId)
            if (state == FullMirrorItemState.COMPLETED || state == FullMirrorItemState.SKIPPED_CONFLICT) {
                completed++
                onProgress(FullMirrorExecutionSummary(completed, warnings, failed, preview.items.size - completed - failed, newUploaded, replaced, previousTrashed, remoteOnlyTrashed))
                continue
            }
            if (state == FullMirrorItemState.UPLOADING || state == FullMirrorItemState.FAILED) {
                // Gmail insert acceptance can be ambiguous. Never repeat the upload automatically.
                failed++
                onProgress(FullMirrorExecutionSummary(completed, warnings, failed, preview.items.size - completed - failed, newUploaded, replaced, previousTrashed, remoteOnlyTrashed))
                continue
            }
            try {
                when (item.action) {
                    FullMirrorAction.UNCHANGED -> {
                        journal.mark(item.itemId, FullMirrorItemState.COMPLETED); completed++
                    }
                    FullMirrorAction.RECOVER_CACHE -> {
                        if (gateway.inspectOldTarget(preview.binding, item) != FullMirrorOldTargetStatus.PRESENT_VALID) error("OWNERSHIP")
                        gateway.recoverCache(item)
                        journal.mark(item.itemId, FullMirrorItemState.COMPLETED); completed++
                    }
                    FullMirrorAction.CONFLICT -> {
                        journal.mark(item.itemId, FullMirrorItemState.SKIPPED_CONFLICT); completed++
                    }
                    FullMirrorAction.FAILED -> {
                        journal.mark(item.itemId, FullMirrorItemState.FAILED, warning = item.reason); failed++
                    }
                    FullMirrorAction.UPLOAD_NEW -> {
                        validateLocal(item)
                        journal.mark(item.itemId, FullMirrorItemState.UPLOADING)
                        val result = gateway.upload(item)
                        gateway.persistUploaded(item, result)
                        journal.mark(item.itemId, FullMirrorItemState.COMPLETED, result)
                        newUploaded++; completed++
                    }
                    FullMirrorAction.REPLACE_CHANGED -> {
                        val previousId = requireNotNull(item.priorGmailMessageId)
                        var result: String? = item.resultingGmailMessageId
                        if (state !in setOf(FullMirrorItemState.PERSISTED, FullMirrorItemState.TRASH_PENDING, FullMirrorItemState.WARNING)) {
                            validateLocal(item)
                            if (gateway.inspectOldTarget(preview.binding, item) != FullMirrorOldTargetStatus.PRESENT_VALID) error("OWNERSHIP")
                            journal.mark(item.itemId, FullMirrorItemState.UPLOADING)
                            result = gateway.upload(item)
                            gateway.persistUploaded(item, result)
                            journal.mark(item.itemId, FullMirrorItemState.TRASH_PENDING, result)
                            state = FullMirrorItemState.TRASH_PENDING
                        }
                        try {
                            val persistedId = requireNotNull(result)
                            if (persistedId == previousId ||
                                !gateway.validatePersistedReplacement(preview.binding, item, persistedId)
                            ) error("REPLACEMENT_NOT_PERSISTED")
                            when (gateway.inspectOldTarget(preview.binding, item)) {
                                FullMirrorOldTargetStatus.PRESENT_VALID -> {
                                    currentCoroutineContext().ensureActive()
                                    gateway.trashOwned(previousId)
                                }
                                FullMirrorOldTargetStatus.ALREADY_TRASHED_VALID -> Unit
                                FullMirrorOldTargetStatus.MISSING -> error("OLD_TARGET_MISSING")
                                FullMirrorOldTargetStatus.AMBIGUOUS -> error("OLD_TARGET_AMBIGUOUS")
                                FullMirrorOldTargetStatus.INVALID -> error("OWNERSHIP")
                            }
                            currentCoroutineContext().ensureActive()
                            journal.mark(item.itemId, FullMirrorItemState.COMPLETED, result)
                            replaced++; previousTrashed++; completed++
                        } catch (error: Throwable) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            val warning = when (error.message) {
                                "REPLACEMENT_NOT_PERSISTED" -> FullMirrorFailureCategory.REPLACEMENT_NOT_PERSISTED
                                "OLD_TARGET_MISSING" -> FullMirrorFailureCategory.OLD_TARGET_MISSING
                                "OLD_TARGET_AMBIGUOUS" -> FullMirrorFailureCategory.OLD_TARGET_AMBIGUOUS
                                "OWNERSHIP" -> FullMirrorFailureCategory.OWNERSHIP
                                else -> FullMirrorFailureCategory.TRASH
                            }
                            journal.mark(item.itemId, FullMirrorItemState.WARNING, result, warning)
                            warnings++
                        }
                    }
                    FullMirrorAction.TRASH_REMOTE_ONLY -> {
                        val previousId = requireNotNull(item.priorGmailMessageId)
                        val oldStatus = gateway.inspectOldTarget(preview.binding, item)
                        if (oldStatus !in setOf(FullMirrorOldTargetStatus.PRESENT_VALID, FullMirrorOldTargetStatus.ALREADY_TRASHED_VALID)) error("OWNERSHIP")
                        journal.mark(item.itemId, FullMirrorItemState.TRASH_PENDING)
                        if (oldStatus == FullMirrorOldTargetStatus.PRESENT_VALID) {
                            currentCoroutineContext().ensureActive()
                            gateway.trashOwned(previousId)
                        }
                        gateway.persistRemoteOnlyRemoval(item)
                        journal.mark(item.itemId, FullMirrorItemState.COMPLETED)
                        remoteOnlyTrashed++; completed++
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val category = if (error.message == "OWNERSHIP") FullMirrorFailureCategory.OWNERSHIP else FullMirrorFailureCategory.CONFLICT
                journal.mark(item.itemId, FullMirrorItemState.FAILED, warning = category)
                failed++
                if (category == FullMirrorFailureCategory.OWNERSHIP) throw error
            }
            onProgress(FullMirrorExecutionSummary(completed, warnings, failed, preview.items.size - completed - failed, newUploaded, replaced, previousTrashed, remoteOnlyTrashed))
        }
        return FullMirrorExecutionSummary(completed, warnings, failed, preview.items.size - completed - failed, newUploaded, replaced, previousTrashed, remoteOnlyTrashed)
    }

    private suspend fun validateLocal(item: FullMirrorPreviewItem) {
        val expected = requireNotNull(item.expectedLocalSourceHash)
        check(gateway.currentLocalSourceHash(item) == expected) { "Local SMS changed after preview." }
    }
}
