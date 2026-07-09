package io.github.isht1008.opensmsbackup.contact

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

class ContactRepository {

    fun getContactName(
        context: Context,
        phoneNumber: String?
    ): String? {

        if (phoneNumber.isNullOrBlank()) {
            return null
        }

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val cursor = context.contentResolver.query(
            lookupUri,
            arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null,
            null,
            null
        )

        cursor?.use {

            if (it.moveToFirst()) {
                return it.getString(
                    it.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )
                )
            }
        }

        return null
    }
}