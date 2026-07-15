package io.github.isht1008.opensmsbackup.gmail.label

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Label
import io.github.isht1008.opensmsbackup.gmail.GmailConstants

class GmailLabelManager(
    private val gmail: Gmail
) {

    private fun listLabels(): List<Label> {

        val response =
            gmail.users()
                .labels()
                .list("me")
                .execute()

        return response.labels ?: emptyList()

    }

    private fun findLabel(
        labels: List<Label>,
        name: String
    ): Label? {

        return labels.firstOrNull {

            it.name == name

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

    private fun getOrCreateLabel(
        labels: MutableList<Label>,
        name: String
    ): Label {

        val existing =
            findLabel(
                labels,
                name
            )

        if (existing != null) {
            return existing
        }

        val created =
            createLabel(
                name
            )

        labels.add(
            created
        )

        return created

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

        return GmailLabels(

            sms = sms.id,

            inbox = inbox.id,

            sent = sent.id,

            drafts = drafts.id,

            failed = failed.id

        )

    }

}