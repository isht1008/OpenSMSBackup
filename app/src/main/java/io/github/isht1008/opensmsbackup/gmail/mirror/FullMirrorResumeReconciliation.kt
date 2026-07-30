package io.github.isht1008.opensmsbackup.gmail.mirror

data class FullMirrorResumeReconciliationResult(
    val allowed: Boolean,
    val expectedActive: Int,
    val attributableAdditions: Int,
    val attributableTrash: Int,
    val unexplainedAdditions: Int,
    val unexplainedRemovals: Int,
    val duplicates: Int,
    val scopedErrors: Int
)

object FullMirrorResumeReconciliation {
    fun evaluate(
        originalIds: Collection<String>,
        resultingIds: Collection<String>,
        trashedOldIds: Collection<String>,
        activeIds: Collection<String>,
        scopedErrors: Int
    ): FullMirrorResumeReconciliationResult {
        val duplicateResults = resultingIds.size - resultingIds.toSet().size
        val duplicateActive = activeIds.size - activeIds.toSet().size
        val expected = (originalIds.toSet() - trashedOldIds.toSet()) + resultingIds
        val active = activeIds.toSet()
        val additions = (active - expected).size
        val removals = (expected - active).size
        val duplicates = duplicateResults + duplicateActive
        return FullMirrorResumeReconciliationResult(
            allowed = additions == 0 && removals == 0 && duplicates == 0 && scopedErrors == 0,
            expectedActive = expected.size,
            attributableAdditions = resultingIds.size,
            attributableTrash = trashedOldIds.size,
            unexplainedAdditions = additions,
            unexplainedRemovals = removals,
            duplicates = duplicates,
            scopedErrors = scopedErrors
        )
    }
}
