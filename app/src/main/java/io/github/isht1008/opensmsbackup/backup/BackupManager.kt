package io.github.isht1008.opensmsbackup.backup

import android.content.Context
import io.github.isht1008.opensmsbackup.conversation.ConversationBuilder
import io.github.isht1008.opensmsbackup.file.BackupFileWriter
import io.github.isht1008.opensmsbackup.json.JsonBackupWriter
import io.github.isht1008.opensmsbackup.sms.SmsRepository

class BackupManager {

    private val smsRepository = SmsRepository()
    private val conversationBuilder = ConversationBuilder()
    private val jsonBackupWriter = JsonBackupWriter()

    fun createBackup(
        context: Context,
        includeContactNames: Boolean,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): BackupResult {

        val messages = smsRepository.getSmsMessages(
            context = context,
            includeContactNames = includeContactNames,
            onProgress = onProgress
        )

        val conversations =
            conversationBuilder.build(messages)

        val result = BackupResult(
            totalMessages = messages.size,
            totalConversations = conversations.size,
            conversations = conversations,
            backupFileName = "",
            backupUri = ""
        )

        // Generate JSON backup
        val json = jsonBackupWriter.createJson(result)

        // Save backup file
        val writer = BackupFileWriter(context)

        val file = writer.write(json)

        println("Backup saved: ${file.uri}")
        println("Filename: ${file.filename}")

        return result.copy(
            backupFileName = file.filename,
            backupUri = file.uri
        )
    }
}