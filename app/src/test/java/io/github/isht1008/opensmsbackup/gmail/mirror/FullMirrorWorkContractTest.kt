package io.github.isht1008.opensmsbackup.gmail.mirror

import org.junit.Assert.*
import org.junit.Test

class FullMirrorWorkContractTest {
    @Test fun `input round trip preserves immutable profile device and run`() {
        val input = FullMirrorWorkInput("run-b", "profile-b", "device-b", 123)
        assertEquals(input, FullMirrorWorkContract.readInput(FullMirrorWorkContract.inputData(input)))
    }
    @Test fun `missing immutable profile binding fails safely`() {
        val data = androidx.work.Data.Builder().putString("mirror.run_id", "run").putString("mirror.device_id", "device").putLong("mirror.created_at", 1).build()
        assertNull(FullMirrorWorkContract.readInput(data))
    }
    @Test fun `progress contains scalar counters only`() {
        val value = FullMirrorWorkProgress(runId = "run", profileId = "profile", maskedAccount = "a***@e***", checked = 2, total = 10, unchanged = 1, recoveries = 0, conflicts = 0, newUploaded = 1, changedReplaced = 1, previousTrashed = 1, remoteOnlyTrashed = 0, warnings = 0, failed = 0, remaining = 8, elapsedMillis = 50, etaSeconds = 4, phase = "phase")
        assertEquals(value, FullMirrorWorkContract.readProgress(FullMirrorWorkContract.progressData(value)))
        assertTrue(FullMirrorWorkContract.progressData(value).keyValueMap.values.all { it is String || it is Int || it is Long })
    }
    @Test fun `terminal output preserves masked binding and scalar counters`() {
        val value = FullMirrorWorkResult("run", "profile", "m***@e***", "COMPLETED", 100, 10, 5, 4, 1, 1, 1, 0, 0, 1, 0, 0, 0, false)
        assertEquals(value, FullMirrorWorkContract.readResult(FullMirrorWorkContract.outputData(value)))
        assertFalse(FullMirrorWorkContract.outputData(value).keyValueMap.values.any { it.toString().contains("example.com") })
    }}
