package io.github.isht1008.opensmsbackup.backup

import android.content.Context
import io.github.isht1008.opensmsbackup.conversation.ConversationBuilder
import io.github.isht1008.opensmsbackup.sms.SmsRepository

class BackupManager {

    private val smsRepository = SmsRepository()
    private val conversationBuilder = ConversationBuilder()

    fun createBackup(
        context: Context,
        includeContactNames: Boolean
    ): BackupResult {

        val messages = smsRepository.getSmsMessages(
            context = context,
            includeContactNames = includeContactNames
        )

        val conversations =
            conversationBuilder.build(messages)

        return BackupResult(
            totalMessages = messages.size,
            totalConversations = conversations.size,
            conversations = conversations
        )
    }
}