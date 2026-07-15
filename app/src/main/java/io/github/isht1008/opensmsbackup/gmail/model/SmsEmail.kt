package io.github.isht1008.opensmsbackup.gmail.model

data class SmsEmail(

    val from: String,

    val to: String,

    val subject: String,

    val body: String,

    val date: Long,

    val headers: Map<String, String>

)