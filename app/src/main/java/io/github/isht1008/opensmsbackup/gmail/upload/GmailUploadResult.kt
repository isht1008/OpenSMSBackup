package io.github.isht1008.opensmsbackup.gmail.upload

data class GmailUploadResult(


    val messageId: String,

    val threadId: String?,

    val labelIds: List<String>,

    val wasUploaded: Boolean = true


)
