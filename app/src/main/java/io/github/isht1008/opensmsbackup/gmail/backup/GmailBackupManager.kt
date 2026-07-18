package io.github.isht1008.opensmsbackup.gmail.backup

import android.content.Context
import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.BackupAccountEntity
import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploader
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import java.util.Locale

class GmailBackupManager {

    private val smsRepository =
        SmsRepository()

    private val conversationBuilder =
        SmsConversationSnapshotBuilder()

    private val mimeMessageBuilder =
        ConversationMimeMessageBuilder()

    suspend fun backup(
        context: Context,
        accountProfile: AccountProfileEntity,
        includeContactNames: Boolean,
        maxConversations: Int? = null,
        onProgress: (
            current: Int,
            total: Int,
            uploaded: Int,
            skipped: Int,
            failed: Int
        ) -> Unit
    ): Result<GmailBackupSummary> {

        return withContext(Dispatchers.IO) {

            try {
                require(
                    accountProfile
                        .accountEmail
                        .isNotBlank()
                ) {
                    "Gmail account email cannot be blank."
                }

                require(
                    maxConversations == null ||
                            maxConversations > 0
                ) {
                    "Conversation limit must be greater than zero."
                }

                val trimmedEmail =
                    accountProfile
                        .accountEmail
                        .trim()

                val accountId =
                    trimmedEmail.lowercase(
                        Locale.ROOT
                    )

                val database =
                    DatabaseProvider.getDatabase(
                        context
                    )

                val accountDao =
                    database.backupAccountDao()

                val snapshotDao =
                    database.conversationSnapshotDao()

                val existingAccount =
                    accountDao.findById(
                        accountId
                    )

                accountDao.insert(
                    existingAccount
                        ?.copy(
                            accountEmail = trimmedEmail,
                            backupEnabled = true
                        )
                        ?: BackupAccountEntity(
                            accountId = accountId,
                            accountEmail = trimmedEmail,
                            isDefault = true,
                            backupEnabled = true
                        )
                )

                val messages =
                    smsRepository.getSmsMessages(
                        context = context,
                        includeContactNames =
                            includeContactNames
                    )

                val conversations =
                    conversationBuilder.build(
                        messages
                    )

                val gmailService =
                    GmailApiClient(context)
                        .createService(
                            accountProfile
                        )

                val uploader =
                    GmailUploader(
                        gmail = gmailService
                    )

                var checked = 0
                var uploaded = 0
                var skipped = 0
                var failed = 0
                var safetyLimitReached = false

                val failures =
                    mutableListOf<String>()

                val warnings =
                    mutableListOf<String>()

                onProgress(
                    checked,
                    conversations.size,
                    uploaded,
                    skipped,
                    failed
                )

                for (conversation in conversations) {

                    currentCoroutineContext()
                        .ensureActive()
                    yield()

                    if (
                        maxConversations != null &&
                        uploaded >= maxConversations
                    ) {
                        safetyLimitReached = true
                        break
                    }

                    val snapshotHash =
                        ConversationSnapshotHashGenerator
                            .generate(
                                conversation
                            )

                    val existingSnapshot =
                        snapshotDao.find(
                            accountId = accountId,
                            androidThreadId =
                                conversation.threadId
                        )

                    if (
                        existingSnapshot?.snapshotHash ==
                        snapshotHash
                    ) {
                        skipped++
                        checked++

                        onProgress(
                            checked,
                            conversations.size,
                            uploaded,
                            skipped,
                            failed
                        )

                        continue
                    }

                    val email =
                        mimeMessageBuilder.build(
                            conversation = conversation,
                            accountEmail = trimmedEmail,
                            snapshotHash = snapshotHash
                        )

                    val uploadResult =
                        uploader.uploadConversation(
                            email
                        )

                    uploadResult
                        .onSuccess { gmailResult ->

                            snapshotDao.insert(
                                ConversationSnapshotEntity(
                                    id =
                                        existingSnapshot?.id
                                            ?: 0L,
                                    accountId = accountId,
                                    accountEmail =
                                        trimmedEmail,
                                    androidThreadId =
                                        conversation.threadId,
                                    address =
                                        conversation.address
                                            .orEmpty(),
                                    contactName =
                                        conversation.contactName,
                                    messageCount =
                                        conversation.messageCount,
                                    snapshotHash =
                                        snapshotHash,
                                    gmailMessageId =
                                        gmailResult.messageId,
                                    gmailThreadId =
                                        gmailResult.threadId,
                                    firstMessageDate =
                                        conversation.firstMessageDate,
                                    lastMessageDate =
                                        conversation.lastMessageDate
                                )
                            )

                            uploaded++

                            val oldMessageId =
                                existingSnapshot
                                    ?.gmailMessageId
                                    ?.takeIf { messageId ->
                                        messageId.isNotBlank() &&
                                                messageId !=
                                                gmailResult.messageId
                                    }

                            if (oldMessageId != null) {
                                uploader.trashMessage(
                                    oldMessageId
                                ).onFailure { error ->

                                    if (warnings.size < 20) {
                                        warnings.add(
                                            buildString {
                                                append(
                                                    "Thread ${conversation.threadId}: new snapshot uploaded, but the previous snapshot could not be moved to Trash"
                                                )

                                                error.message
                                                    ?.takeIf { message ->
                                                        message.isNotBlank()
                                                    }
                                                    ?.let { message ->
                                                        append(" - ")
                                                        append(message)
                                                    }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        .onFailure { error ->
                            failed++

                            if (failures.size < 20) {
                                failures.add(
                                    buildString {
                                        append(
                                            "Thread ${conversation.threadId}"
                                        )

                                        conversation.address
                                            ?.takeIf { address ->
                                                address.isNotBlank()
                                            }
                                            ?.let { address ->
                                                append(" ($address)")
                                            }

                                        append(": ")
                                        append(
                                            error.javaClass.simpleName
                                        )
                                        append(" - ")
                                        append(
                                            error.message
                                                ?: "Unknown error"
                                        )
                                    }
                                )
                            }
                        }

                    checked++

                    onProgress(
                        checked,
                        conversations.size,
                        uploaded,
                        skipped,
                        failed
                    )
                }

                val now =
                    System.currentTimeMillis()

                accountDao.insert(
                    accountDao.findById(accountId)
                        ?.copy(
                            lastBackupTime = now,
                            lastSyncTime = now
                        )
                        ?: BackupAccountEntity(
                            accountId = accountId,
                            accountEmail = trimmedEmail,
                            lastBackupTime = now,
                            lastSyncTime = now,
                            isDefault = true
                        )
                )

                Result.success(
                    GmailBackupSummary(
                        totalMessages = messages.size,
                        totalConversations =
                            conversations.size,
                        checkedConversations = checked,
                        uploadedConversations = uploaded,
                        skippedConversations = skipped,
                        failedConversations = failed,
                        stoppedAtSafetyLimit =
                            safetyLimitReached,
                        failures = failures,
                        warnings = warnings
                    )
                )

            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
}
