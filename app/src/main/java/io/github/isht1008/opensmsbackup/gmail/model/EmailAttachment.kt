package io.github.isht1008.opensmsbackup.gmail.model

data class EmailAttachment(

    val fileName: String,

    val mimeType: String,

    val content: ByteArray
)
