package io.github.isht1008.opensmsbackup.sms

import io.github.isht1008.opensmsbackup.contact.ContactRepository
import android.content.Context
import android.provider.Telephony

class SmsRepository {
    private val contactRepository = ContactRepository()

    private val contactCache = mutableMapOf<String, String?>()

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

            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)


            var count = 0

            while (it.moveToNext() && count < 100) {

                count++
                val address = it.getString(addressIndex)

                val contactName =
                    if (!includeContactNames || address.isNullOrBlank()) {
                        null
                    } else {
                        contactCache.getOrPut(address) {
                            contactRepository.getContactName(
                                context,
                                address
                            )
                        }
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