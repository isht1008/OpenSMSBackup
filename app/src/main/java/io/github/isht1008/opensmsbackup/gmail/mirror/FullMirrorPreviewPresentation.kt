package io.github.isht1008.opensmsbackup.gmail.mirror

data class FullMirrorPreviewPresentationState(
    val title: String,
    val showConfirmationAction: Boolean,
    val showTypedConfirmation: Boolean,
    val primaryActionLabel: String
)

object FullMirrorPreviewPresentation {
    fun resolve(preview: FullMirrorPreview): FullMirrorPreviewPresentationState {
        val executable = preview.executionAllowed
        return FullMirrorPreviewPresentationState(
            title = if (executable) "Full Mirror preview" else "Full Mirror cannot run yet",
            showConfirmationAction = executable,
            showTypedConfirmation = executable && preview.trashCount > 0,
            primaryActionLabel = if (executable) "Confirm Full Mirror" else "Close"
        )
    }
}