package io.github.isht1008.opensmsbackup.sms

import android.content.Context
import android.provider.Telephony
import io.github.isht1008.opensmsbackup.contact.ContactRepository
import io.github.isht1008.opensmsbackup.util.DateUtils

data class SmsReadCompletenessResult(
    val messages: List<SmsMessage>,
    val providerCount: Int?,
    val complete: Boolean,
    val failureCategory: String?
)
class SmsRepository {

    private val contactRepository =
        ContactRepository()

    fun getSmsCount(
        context: Context
    ): Int {

        return context.contentResolver
            .query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID
                ),
                null,
                null,
                null
            )
            ?.use { cursor ->

                cursor.count

            }
            ?: 0
    }

    fun getSmsMessages(
        context: Context,
        includeContactNames: Boolean,
        onProgress: (
            (
            current: Int,
            total: Int
        ) -> Unit
        )? = null
    ): List<SmsMessage> {

        val smsList =
            mutableListOf<SmsMessage>()

        val totalMessages =
            getSmsCount(context)

        val contacts =
            if (includeContactNames) {

                contactRepository.loadContacts(
                    context
                )

            } else {

                emptyMap()

            }

        val projection =
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.SUBSCRIPTION_ID,
                Telephony.Sms.READ,
                Telephony.Sms.SERVICE_CENTER
            )

        context.contentResolver
            .query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
            ?.use { cursor ->

                val idIndex =
                    cursor.getColumnIndexOrThrow(
                        Telephony.Sms._ID
                    )

                val threadIdIndex =
                    cursor.getColumnIndex(
                        Telephony.Sms.THREAD_ID
                    )

                val addressIndex =
                    cursor.getColumnIndexOrThrow(
                        Telephony.Sms.ADDRESS
                    )

                val bodyIndex =
                    cursor.getColumnIndexOrThrow(
                        Telephony.Sms.BODY
                    )

                val dateIndex =
                    cursor.getColumnIndexOrThrow(
                        Telephony.Sms.DATE
                    )

                val typeIndex =
                    cursor.getColumnIndexOrThrow(
                        Telephony.Sms.TYPE
                    )

                val subscriptionIdIndex =
                    cursor.getColumnIndex(
                        Telephony.Sms.SUBSCRIPTION_ID
                    )

                val readIndex =
                    cursor.getColumnIndex(
                        Telephony.Sms.READ
                    )

                val serviceCenterIndex =
                    cursor.getColumnIndex(
                        Telephony.Sms.SERVICE_CENTER
                    )

                var current = 0

                while (cursor.moveToNext()) {

                    current++

                    if (
                        current % 250 == 0 ||
                        current == totalMessages
                    ) {

                        onProgress?.invoke(
                            current,
                            totalMessages
                        )
                    }

                    val address =
                        cursor.getNullableString(
                            addressIndex
                        )

                    val contactName =
                        if (
                            !includeContactNames ||
                            address.isNullOrBlank()
                        ) {

                            null

                        } else {

                            contacts[address]

                        }

                    val smsDate =
                        cursor.getLong(
                            dateIndex
                        )

                    smsList.add(
                        SmsMessage(
                            id = cursor.getLong(
                                idIndex
                            ),
                            threadId =
                                cursor.getNullableLong(
                                    threadIdIndex
                                ) ?: 0L,
                            address = address,
                            contactName = contactName,
                            body =
                                cursor.getNullableString(
                                    bodyIndex
                                ),
                            date = smsDate,
                            dateFormatted =
                                DateUtils.formatDateIST(
                                    smsDate
                                ),
                            type = cursor.getInt(
                                typeIndex
                            ),
                            subscriptionId =
                                cursor.getNullableInt(
                                    subscriptionIdIndex
                                ),
                            isRead =
                                cursor.getNullableInt(
                                    readIndex
                                )?.let { value ->
                                    value != 0
                                } ?: true,
                            serviceCenter =
                                cursor.getNullableString(
                                    serviceCenterIndex
                                )
                        )
                    )
                }
            }

        if (totalMessages == 0) {

            onProgress?.invoke(
                0,
                0
            )
        }

        return smsList
    }


    fun getCompleteSmsMessages(
        context: Context,
        includeContactNames: Boolean
    ): SmsReadCompletenessResult {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_SMS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return SmsReadCompletenessResult(emptyList(), null, false, "PERMISSION_MISSING")
        }
        return try {
            val before = queryCountOrNull(context)
                ?: return SmsReadCompletenessResult(emptyList(), null, false, "COUNT_UNAVAILABLE")
            val messages = getSmsMessages(context, includeContactNames)
            val after = queryCountOrNull(context)
                ?: return SmsReadCompletenessResult(messages, null, false, "COUNT_UNAVAILABLE")
            val consistent = before == after && messages.size == after
            SmsReadCompletenessResult(
                messages = messages,
                providerCount = after,
                complete = consistent,
                failureCategory = if (consistent) null else "COUNT_INCONSISTENT"
            )
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            SmsReadCompletenessResult(emptyList(), null, false, "QUERY_FAILED")
        }
    }

    private fun queryCountOrNull(context: Context): Int? =
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null,
            null,
            null
        )?.use { it.count }
    private fun android.database.Cursor.getNullableString(
        columnIndex: Int
    ): String? {

        if (
            columnIndex < 0 ||
            isNull(columnIndex)
        ) {
            return null
        }

        return getString(columnIndex)
    }

    private fun android.database.Cursor.getNullableLong(
        columnIndex: Int
    ): Long? {

        if (
            columnIndex < 0 ||
            isNull(columnIndex)
        ) {
            return null
        }

        return getLong(columnIndex)
    }

    private fun android.database.Cursor.getNullableInt(
        columnIndex: Int
    ): Int? {

        if (
            columnIndex < 0 ||
            isNull(columnIndex)
        ) {
            return null
        }

        return getInt(columnIndex)
    }
}
