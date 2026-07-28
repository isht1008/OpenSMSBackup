package io.github.isht1008.opensmsbackup.gmail.work

import kotlin.math.ceil

data class GmailBackupTimeEstimate(
    val elapsedMillis: Long,
    val remainingSeconds: Long?
)

class GmailBackupEtaEstimator(
    private val startedElapsedRealtime: Long,
    private val nowElapsedRealtime: () -> Long
) {
    private var phaseGroup: PhaseGroup? = null
    private var sampleTime = startedElapsedRealtime
    private var sampleChecked = 0
    private var ewmaMillisPerUnit: Double? = null
    private var samples = 0

    fun update(
        phase: GmailBackupPhase,
        checked: Int,
        total: Int,
        terminal: Boolean = false
    ): GmailBackupTimeEstimate {
        val now = nowElapsedRealtime()
        val elapsed = (now - startedElapsedRealtime).coerceAtLeast(0L)
        if (terminal) return GmailBackupTimeEstimate(elapsed, null)

        val group = phase.group()
        if (group != phaseGroup) {
            phaseGroup = group
            sampleTime = now
            sampleChecked = checked
            ewmaMillisPerUnit = null
            samples = 0
        } else if (checked > sampleChecked) {
            val duration = (now - sampleTime).coerceAtLeast(0L)
            val units = checked - sampleChecked
            if (duration > 0L && units > 0) {
                val sample = duration.toDouble() / units
                ewmaMillisPerUnit = ewmaMillisPerUnit?.let { previous ->
                    ETA_ALPHA * sample + (1.0 - ETA_ALPHA) * previous
                } ?: sample
                samples++
            }
            sampleTime = now
            sampleChecked = checked
        }

        val remaining = (total - checked).coerceAtLeast(0)
        val eta = when {
            remaining == 0 -> null
            samples < MINIMUM_SAMPLES -> null
            else -> ceil(requireNotNull(ewmaMillisPerUnit) * remaining / 1_000.0)
                .toLong()
                .coerceAtLeast(1L)
        }
        return GmailBackupTimeEstimate(elapsed, eta)
    }

    private fun GmailBackupPhase.group(): PhaseGroup = when (this) {
        GmailBackupPhase.ENQUEUING,
        GmailBackupPhase.PREPARING,
        GmailBackupPhase.READING,
        GmailBackupPhase.CHECKING_LOCAL,
        GmailBackupPhase.CONNECTING -> PhaseGroup.LOCAL
        GmailBackupPhase.INDEXING -> PhaseGroup.INDEX
        GmailBackupPhase.COMPARING,
        GmailBackupPhase.UPLOADING,
        GmailBackupPhase.RUNNING,
        GmailBackupPhase.RETRYING -> PhaseGroup.REMOTE
        GmailBackupPhase.CANCELLING,
        GmailBackupPhase.FINALIZING -> PhaseGroup.FINAL
    }

    private enum class PhaseGroup { LOCAL, INDEX, REMOTE, FINAL }

    private companion object {
        const val ETA_ALPHA = 0.25
        const val MINIMUM_SAMPLES = 2
    }
}
