package io.github.isht1008.opensmsbackup.model

data class BackupFile(

    val formatVersion: Int,

    val createdAt: String,

    val applicationName: String,

    val applicationVersion: String,

    val totalMessages: Int,

    val totalConversations: Int,

    val conversations: List<Conversation>

)