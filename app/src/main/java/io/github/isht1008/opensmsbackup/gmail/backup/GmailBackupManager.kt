package io.github.isht1008.opensmsbackup.gmail.backup

import android.content.Context
import io.github.isht1008.opensmsbackup.backup.fingerprint.SmsFingerprintGenerator
import io.github.isht1008.opensmsbackup.database.BackupAccountEntity
import io.github.isht1008.opensmsbackup.database.BackupMessageEntity
import io.github.isht1008.opensmsbackup.database.DatabaseProvider
import io.github.isht1008.opensmsbackup.gmail.api.GmailApiClient
import io.github.isht1008.opensmsbackup.gmail.mime.MimeMessageBuilder
import io.github.isht1008.opensmsbackup.gmail.upload.GmailUploader
import io.github.isht1008.opensmsbackup.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class GmailBackupManager {

    private val smsRepository =
        SmsRepository()

    private val mimeMessageBuilder =
        MimeMessageBuilder()

    suspend fun backup(
        context: Context,
        accountEmail: String,
        includeContactNames: Boolean,
        maxNewMessages: Int? = null,
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

                require(accountEmail.isNotBlank()) {
                    "Gmail account email cannot be blank."
                }

                val trimmedEmail =
                    accountEmail.trim()

                val normalizedEmail =
                    trimmedEmail.lowercase(
                        Locale.ROOT
                    )

                val accountId =
                    normalizedEmail

                val database =
                    DatabaseProvider.getDatabase(
                        context
                    )

                val accountDao =
                    database.backupAccountDao()

                val messageDao =
                    database.backupMessageDao()

                val existingAccount =
                    accountDao.findById(
                        accountId
                    )

                accountDao.insert(
                    existingAccount
                        ?.copy(
                            accountEmail =
                                trimmedEmail,
                            backupEnabled = true
                        )
                        ?: BackupAccountEntity(
                            accountId = accountId,
                            accountEmail =
                                trimmedEmail,
                            isDefault = true,
                            backupEnabled = true
                        )
                )

                val messages =
                    smsRepository
                        .getSmsMessages(
                            context = context,
                            includeContactNames =
                                includeContactNames
                        )
                        .sortedWith(
                            compareBy(
                                { message ->
                                    message.date
                                },
                                { message ->
                                    message.id
                                }
                            )
                        )

                val gmailService =
                    GmailApiClient(context)
                        .createService(
                            trimmedEmail
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

                val activeGmailThreads =
                    mutableMapOf<Long, String>()

                val activeParentMessageIds =
                    mutableMapOf<Long, String>()

                for (sms in messages) {

                    if (
                        maxNewMessages != null &&
                        uploaded >= maxNewMessages
                    ) {

                        safetyLimitReached = true
                        break
                    }

                    checked++

                    val fingerprint =
                        SmsFingerprintGenerator.generate(
                            address = sms.address,
                            body = sms.body,
                            date = sms.date,
                            type = sms.type
                        )

                    val existing =
                        messageDao.findByFingerprint(
                            accountEmail =
                                trimmedEmail,
                            fingerprint =
                                fingerprint
                        )

                    if (existing != null) {

                        existing.gmailThreadId
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let { gmailThreadId ->

                                activeGmailThreads[
                                    sms.threadId
                                ] = gmailThreadId
                            }

                        activeParentMessageIds[
                            sms.threadId
                        ] =
                            MimeMessageBuilder
                                .messageIdForFingerprint(
                                    existing.fingerprint
                                )

                        skipped++

                        onProgress(
                            checked,
                            messages.size,
                            uploaded,
                            skipped,
                            failed
                        )

                        continue
                    }

                    val previousThreadMessage =
                        if (
                            activeGmailThreads.containsKey(
                                sms.threadId
                            )
                        ) {
                            null
                        } else {
                            messageDao
                                .findLatestThreadMessage(
                                    accountId =
                                        accountId,
                                    androidThreadId =
                                        sms.threadId
                                )
                        }

                    val gmailThreadId =
                        activeGmailThreads[
                            sms.threadId
                        ]
                            ?: previousThreadMessage
                                ?.gmailThreadId

                    if (!gmailThreadId.isNullOrBlank()) {

                        activeGmailThreads[
                            sms.threadId
                        ] = gmailThreadId
                    }

                    val parentMessageId =
                        activeParentMessageIds[
                            sms.threadId
                        ]
                            ?: previousThreadMessage
                                ?.fingerprint
                                ?.let { previousFingerprint ->

                                    MimeMessageBuilder
                                        .messageIdForFingerprint(
                                            previousFingerprint
                                        )
                                }

                    val smsEmail =
                        mimeMessageBuilder.build(
                            sms = sms,
                            accountEmail =
                                trimmedEmail,
                            fingerprint =
                                fingerprint,
                            parentMessageId =
                                parentMessageId
                        )

                    val uploadResult =
                        uploader.upload(
                            email = smsEmail,
                            smsType = sms.smsType,
                            gmailThreadId =
                                gmailThreadId
                        )

                    uploadResult
                        .onSuccess { gmailResult ->

                            val uploadedThreadId =
                                requireNotNull(
                                    gmailResult.threadId
                                ) {
                                    "Gmail did not return a thread ID."
                                }

                            val insertedId =
                                messageDao.insert(
                                    BackupMessageEntity(
                                        accountId =
                                            accountId,
                                        accountEmail =
                                            trimmedEmail,
                                        smsId =
                                            sms.id,
                                        threadId =
                                            sms.threadId,
                                        address =
                                            sms.address.orEmpty(),
                                        messageDate =
                                            sms.date,
                                        messageType =
                                            sms.type,
                                        fingerprint =
                                            fingerprint,
                                        gmailMessageId =
                                            gmailResult.messageId,
                                        gmailThreadId =
                                            uploadedThreadId
                                    )
                                )

                            if (insertedId == -1L) {

                                skipped++

                            } else {

                                uploaded++

                                activeGmailThreads[
                                    sms.threadId
                                ] =
                                    uploadedThreadId

                                activeParentMessageIds[
                                    sms.threadId
                                ] =
                                    MimeMessageBuilder
                                        .messageIdForFingerprint(
                                            fingerprint
                                        )
                            }
                        }
                        .onFailure { error ->

                            failed++

                            if (failures.size < 20) {

                                failures.add(
                                    buildString {

                                        append(
                                            "SMS ID "
                                        )

                                        append(
                                            sms.id
                                        )

                                        append(
                                            ": "
                                        )

                                        append(
                                            error.javaClass
                                                .simpleName
                                        )

                                        append(
                                            " - "
                                        )

                                        append(
                                            error.message
                                                ?: "Unknown error"
                                        )
                                    }
                                )
                            }
                        }

                    onProgress(
                        checked,
                        messages.size,
                        uploaded,
                        skipped,
                        failed
                    )
                }

                val now =
                    System.currentTimeMillis()

                accountDao.insert(
                    accountDao
                        .findById(accountId)
                        ?.copy(
                            lastBackupTime = now,
                            lastSyncTime = now
                        )
                        ?: BackupAccountEntity(
                            accountId =
                                accountId,
                            accountEmail =
                                trimmedEmail,
                            lastBackupTime =
                                now,
                            lastSyncTime =
                                now,
                            isDefault =
                                true
                        )
                )

                Result.success(
                    GmailBackupSummary(
                        totalMessages =
                            messages.size,
                        checkedMessages =
                            checked,
                        uploadedMessages =
                            uploaded,
                        skippedMessages =
                            skipped,
                        failedMessages =
                            failed,
                        stoppedAtSafetyLimit =
                            safetyLimitReached,
                        failures =
                            failures
                    )
                )

            } catch (error: Exception) {

                Result.failure(
                    error
                )
            }
        }
    }
}