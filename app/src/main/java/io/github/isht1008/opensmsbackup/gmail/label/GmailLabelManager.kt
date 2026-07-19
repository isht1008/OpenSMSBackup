package io.github.isht1008.opensmsbackup.gmail.label

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Label
import io.github.isht1008.opensmsbackup.gmail.GmailConstants
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy
import io.github.isht1008.opensmsbackup.device.DeviceDisplayName
import io.github.isht1008.opensmsbackup.device.DeviceProfile

class GmailLabelManager(
    private val gmail: Gmail,
    private val profileId: String = "unknown",
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy(),
    private val onRetry: suspend (Int, Int) -> Unit = { _, _ -> },
    private val deviceProfile: DeviceProfile? = null,
    private val cachedDeviceLabelId: String? = null,
    private val onDeviceLabelResolved: suspend (String) -> Unit = {}
) {

    private suspend fun listLabels(): List<Label> {

        val response = retryPolicy.execute(
            operationName = "list_labels",
            profileId = profileId,
            onRetry = onRetry
        ) {
            gmail.users()
                .labels()
                .list("me")
                .execute()
        }

        return response.labels ?: emptyList()
    }

    private fun findLabel(
        labels: List<Label>,
        name: String
    ): Label? {

        return labels.firstOrNull { label ->
            label.name == name
        }
    }

    private fun createLabel(
        name: String
    ): Label {

        val label =
            Label().apply {
                this.name = name
                labelListVisibility = "labelShow"
                messageListVisibility = "show"
            }

        return gmail.users()
            .labels()
            .create("me", label)
            .execute()
    }

    private suspend fun getOrCreateLabel(
        labels: MutableList<Label>,
        name: String
    ): Label {

        val existing =
            findLabel(
                labels = labels,
                name = name
            )

        if (existing != null) {
            return existing
        }

        return createLabel(name)
            .also { created ->
                labels.add(created)
            }
    }

    suspend fun ensureLabels(): GmailLabels {

        val labels =
            listLabels()
                .toMutableList()

        val sms =
            getOrCreateLabel(
                labels,
                GmailConstants.LABEL_SMS
            )

        val conversations =
            getOrCreateLabel(
                labels,
                GmailConstants.LABEL_SMS_CONVERSATIONS
            )

        val inbox =
            getOrCreateLabel(
                labels,
                GmailConstants.LABEL_SMS_INBOX
            )

        val sent =
            getOrCreateLabel(
                labels,
                GmailConstants.LABEL_SMS_SENT
            )

        val drafts =
            getOrCreateLabel(
                labels,
                GmailConstants.LABEL_SMS_DRAFTS
            )

        val failed =
            getOrCreateLabel(
                labels,
                GmailConstants.LABEL_SMS_FAILED
            )

        val deviceConversations = deviceProfile?.let { profile ->
            getOrCreateLabel(labels, "SMS/Devices")
            val baseSegment = DeviceDisplayName.gmailLabelSegment(
                profile.displayName,
                profile.primaryPhoneNumber,
                profile.deviceId,
                requireStableSuffix = false
            )
            val basePath = "SMS/Devices/$baseSegment"
            val cached = cachedDeviceLabelId?.let { id -> labels.firstOrNull { it.id == id } }
            val desiredPath = "$basePath/Conversations"
            val collision = labels.any { it.name == desiredPath && it.id != cached?.id }
            val resolvedSegment = if (collision) {
                DeviceDisplayName.gmailLabelSegment(
                    profile.displayName,
                    profile.primaryPhoneNumber,
                    profile.deviceId,
                    requireStableSuffix = true
                )
            } else baseSegment
            val resolvedParent = "SMS/Devices/$resolvedSegment"
            getOrCreateLabel(labels, resolvedParent)
            val resolvedPath = "$resolvedParent/Conversations"
            val label = if (cached != null) {
                if (cached.name != resolvedPath) {
                    retryPolicy.execute(
                        operationName = "rename_device_label",
                        profileId = profileId,
                        onRetry = onRetry
                    ) {
                        gmail.users().labels().update(
                            "me",
                            requireNotNull(cached.id),
                            Label().apply {
                                name = resolvedPath
                                labelListVisibility = "labelShow"
                                messageListVisibility = "show"
                            }
                        ).execute()
                    }
                } else cached
            } else getOrCreateLabel(labels, resolvedPath)
            requireNotNull(label.id).also { onDeviceLabelResolved(it) }
        }

        return GmailLabels(
            sms = requireNotNull(sms.id),
            conversations =
                requireNotNull(conversations.id),
            inbox = requireNotNull(inbox.id),
            sent = requireNotNull(sent.id),
            drafts = requireNotNull(drafts.id),
            failed = requireNotNull(failed.id),
            deviceConversations = deviceConversations
        )
    }
}
