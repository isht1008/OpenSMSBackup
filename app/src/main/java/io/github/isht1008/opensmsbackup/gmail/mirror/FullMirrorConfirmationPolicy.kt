package io.github.isht1008.opensmsbackup.gmail.mirror

object FullMirrorConfirmationPolicy {
    data class Decision(val allowed: Boolean, val reason: String? = null, val highRisk: Boolean = false)

    fun evaluate(preview: FullMirrorPreview, typedText: String?, now: Long): Decision = when {
        now > preview.expiresAt -> Decision(false, "Preview expired. Create a new preview.")
        !preview.executionAllowed -> Decision(false, "Resolve conflicts, unreadable items, or local scan errors first.", preview.highRisk)
        preview.trashCount > 0 && typedText?.trim() != "MIRROR ${preview.trashCount}" ->
            Decision(false, "Type MIRROR ${preview.trashCount} to confirm recoverable Trash moves.", preview.highRisk)
        else -> Decision(true, highRisk = preview.highRisk)
    }
}
