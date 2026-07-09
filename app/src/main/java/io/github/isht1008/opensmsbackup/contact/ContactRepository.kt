package io.github.isht1008.opensmsbackup.contact

import android.content.Context
import android.provider.ContactsContract

class ContactRepository {

    fun loadContacts(
        context: Context
    ): Map<String, String> {

        val contacts = mutableMapOf<String, String>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null,
            null,
            null
        )

        cursor?.use {

            val numberIndex = it.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val nameIndex = it.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            while (it.moveToNext()) {

                val number = it.getString(numberIndex)
                val name = it.getString(nameIndex)

                if (!number.isNullOrBlank()) {
                    contacts[number] = name
                }
            }
        }

        return contacts
    }
}