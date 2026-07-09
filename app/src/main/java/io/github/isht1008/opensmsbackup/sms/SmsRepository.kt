package io.github.isht1008.opensmsbackup.sms

import android.content.Context
import android.provider.Telephony
import io.github.isht1008.opensmsbackup.contact.ContactRepository

class SmsRepository {

    private val contactRepository = ContactRepository()

    fun getSmsCount(context: Context): Int {

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null,
            null,
            null
        )

        val count = cursor?.count ?: 0

        cursor?.close()

        return count
    }

    fun getSmsMessages(
        context: Context,
        includeContactNames: Boolean
    ): List<SmsMessage> {

        val smsList = mutableListOf<SmsMessage>()

        val contacts =
            if (includeContactNames) {
                contactRepository.loadContacts(context)
            } else {
                emptyMap()
            }

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {

            val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (it.moveToNext()) {

                val address = it.getString(addressIndex)

                val contactName =
                    if (!includeContactNames || address.isNullOrBlank()) {
                        null
                    } else {
                        contacts[address]
                    }

                smsList.add(
                    SmsMessage(
                        id = it.getString(idIndex),
                        address = address,
                        contactName = contactName,
                        body = it.getString(bodyIndex),
                        date = it.getLong(dateIndex),
                        type = it.getInt(typeIndex)
                    )
                )
            }
        }

        return smsList
    }
}