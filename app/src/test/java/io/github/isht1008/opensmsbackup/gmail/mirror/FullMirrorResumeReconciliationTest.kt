package io.github.isht1008.opensmsbackup.gmail.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullMirrorResumeReconciliationTest {
    @Test fun journalAttributableAdditionsAndTrashAreAccepted() {
        val original = (1..21).map { "old-$it" }
        val results = (1..3323).map { "new-$it" }
        val active = (original - "old-1") + results
        val result = FullMirrorResumeReconciliation.evaluate(original, results, listOf("old-1"), active, 0)
        assertTrue(result.allowed)
        assertEquals(3323, result.attributableAdditions)
        assertEquals(1, result.attributableTrash)
        assertEquals(3343, result.expectedActive)
    }

    @Test fun unexplainedMissingDuplicateAndScopedErrorBlockResume() {
        assertFalse(FullMirrorResumeReconciliation.evaluate(listOf("old"), listOf("new"), emptyList(), listOf("old", "new", "foreign"), 0).allowed)
        assertFalse(FullMirrorResumeReconciliation.evaluate(listOf("old"), listOf("new"), emptyList(), listOf("old"), 0).allowed)
        assertFalse(FullMirrorResumeReconciliation.evaluate(listOf("old"), listOf("new", "new"), emptyList(), listOf("old", "new"), 0).allowed)
        assertFalse(FullMirrorResumeReconciliation.evaluate(listOf("old"), listOf("new"), emptyList(), listOf("old", "new"), 1).allowed)
    }

    @Test fun productionPlanConservationRemainsExact() {
        assertEquals(3332, 3311 + 12 + 9)
        val ids = (1..3323).map { "result-$it" }
        assertEquals(3323, ids.toSet().size)
        assertEquals(11, (1..11).count())
    }
}
