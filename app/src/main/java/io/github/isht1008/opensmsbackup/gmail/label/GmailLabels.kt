package io.github.isht1008.opensmsbackup.gmail.label

data class GmailLabels(

    val sms: String,

    val conversations: String,

    val inbox: String,

    val sent: String,

    val drafts: String,

    val failed: String,

    val deviceConversations: String? = null
)
