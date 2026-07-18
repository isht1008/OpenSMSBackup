package io.github.isht1008.opensmsbackup.gmail.upload

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabelManager
import io.github.isht1008.opensmsbackup.gmail.label.GmailLabels
import io.github.isht1008.opensmsbackup.gmail.mime.GmailMessageEncoder
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.gmail.error.GmailRetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class GmailUploader(
    private val gmail: Gmail,
    private val profileId: String,
    private val retryPolicy: GmailRetryPolicy = GmailRetryPolicy(),
    private val onRetry: (Int, Int) -> Unit = { _, _ -> },
    private val labelManager: GmailLabelManager =
        GmailLabelManager(
            gmail = gmail,
            profileId = profileId,
            retryPolicy = retryPolicy,
            onRetry = onRetry
        ),
    private val messageEncoder: GmailMessageEncoder =
        GmailMessageEncoder()
) {

    private var cachedLabels: GmailLabels? =
        null

    suspend fun uploadConversation(
        email: SmsEmail
    ): Result<GmailUploadResult> {

        return withContext(Dispatchers.IO) {

            try {
                val labels =
                    getLabels()

                val labelIds =
                    listOf(
                        labels.sms,
                        labels.conversations
                    ).distinct()

                val message =
                    Message().apply {
                        raw =
                            messageEncoder.encode(
                                email
                            )
                        this.labelIds = labelIds
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

                Result.success(
                    GmailUploadResult(
                        messageId =
                            requireNotNull(uploaded.id) {
                                "Gmail did not return a message ID."
                            },
                        threadId = uploaded.threadId,
                        labelIds =
                            uploaded.labelIds ?: labelIds
                    )
                )

            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    suspend fun trashMessage(
        gmailMessageId: String
    ): Result<Unit> {

        return withContext(Dispatchers.IO) {

            try {
                require(gmailMessageId.isNotBlank()) {
                    "Gmail message ID cannot be blank."
                }

                retryPolicy.execute(
                    operationName = "trash_message",
                    profileId = profileId,
                    onRetry = onRetry
                ) {
                    gmail.users()
                        .messages()
                        .trash(
                            "me",
                            gmailMessageId
                        )
                        .execute()
                }

                Result.success(Unit)

            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
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
                cachedLabels = labels
            }
    }
}
