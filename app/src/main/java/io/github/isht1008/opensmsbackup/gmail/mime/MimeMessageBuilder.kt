package io.github.isht1008.opensmsbackup.gmail.mime

import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.sms.SmsMessage

class MimeMessageBuilder {

    fun build(
        sms: SmsMessage,
        accountEmail: String,
        fingerprint: String
    ): SmsEmail {

        return SmsEmail(
            from = buildFrom(
                sms = sms,
                accountEmail = accountEmail
            ),
            to = buildTo(
                sms = sms,
                accountEmail = accountEmail
            ),
            subject = buildSubject(
                sms
            ),
            body = buildBody(
                sms
            ),
            date = sms.date,
            headers = buildHeaders(
                sms = sms,
                accountEmail = accountEmail,
                fingerprint = fingerprint
            )
        )
    }

    private fun buildFrom(
        sms: SmsMessage,
        accountEmail: String
    ): String {

        TODO("Implement From header")
    }

    private fun buildTo(
        sms: SmsMessage,
        accountEmail: String
    ): String {

        TODO("Implement To header")
    }

    private fun buildSubject(
        sms: SmsMessage
    ): String {

        TODO("Implement subject generation")
    }

    private fun buildBody(
        sms: SmsMessage
    ): String {

        TODO("Implement email body generation")
    }

    private fun buildHeaders(
        sms: SmsMessage,
        accountEmail: String,
        fingerprint: String
    ): Map<String, String> {

        TODO("Implement custom email headers")
    }
}
