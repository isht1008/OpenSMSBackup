package io.github.isht1008.opensmsbackup.gmail.upload

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabelManager
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabels
import io.github.isht1008.opensmsbackup.gmail.mime.GmailMessageEncoder
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.sms.SmsType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GmailUploader(
    private val gmail: Gmail,
    private val labelManager: GmailLabelManager =
        GmailLabelManager(gmail),
    private val messageEncoder: GmailMessageEncoder =
        GmailMessageEncoder()
) {

    private var cachedLabels: GmailLabels? =
        null

    suspend fun upload(
        email: SmsEmail,
        smsType: SmsType,
        gmailThreadId: String?
    ): Result<GmailUploadResult> {

        return withContext(Dispatchers.IO) {

            try {

                val labels =
                    getLabels()

                val labelIds =
                    buildLabelIds(
                        labels = labels,
                        smsType = smsType
                    )

                val message =
                    Message().apply {

                        raw =
                            messageEncoder.encode(
                                email = email
                            )

                        this.labelIds =
                            labelIds

                        if (!gmailThreadId.isNullOrBlank()) {

                            threadId =
                                gmailThreadId
                        }
                    }

                val uploaded =
                    gmail.users()
                        .messages()
                        .insert(
                            "me",
                            message
                        )
                        .setInternalDateSource(
                            "dateHeader"
                        )
                        .execute()

                val messageId =
                    requireNotNull(
                        uploaded.id
                    ) {
                        "Gmail did not return a message ID."
                    }

                val uploadedThreadId =
                    requireNotNull(
                        uploaded.threadId
                    ) {
                        "Gmail did not return a thread ID."
                    }

                Result.success(
                    GmailUploadResult(
                        messageId = messageId,
                        threadId = uploadedThreadId,
                        labelIds =
                            uploaded.labelIds
                                ?: labelIds
                    )
                )

            } catch (error: Exception) {

                Result.failure(
                    error
                )
            }
        }
    }

    private suspend fun getLabels(): GmailLabels {

        val existing =
            cachedLabels

        if (existing != null) {
            return existing
        }

        return labelManager
            .ensureLabels()
            .also { labels ->

                cachedLabels =
                    labels
            }
    }

    private fun buildLabelIds(
        labels: GmailLabels,
        smsType: SmsType
    ): List<String> {

        val directionalLabel =
            when (smsType) {

                SmsType.RECEIVED ->
                    labels.inbox

                SmsType.SENT ->
                    labels.sent

                SmsType.DRAFT ->
                    labels.drafts

                SmsType.OUTBOX,
                SmsType.FAILED,
                SmsType.QUEUED,
                SmsType.ALL,
                SmsType.UNKNOWN ->
                    labels.failed
            }

        return listOf(
            labels.sms,
            directionalLabel
        ).distinct()
    }

}