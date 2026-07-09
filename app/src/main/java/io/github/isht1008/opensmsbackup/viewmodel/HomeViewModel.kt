package io.github.isht1008.opensmsbackup.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.isht1008.opensmsbackup.sms.SmsRepository

class HomeViewModel : ViewModel() {

    var status by mutableStateOf("Ready")
        private set

    fun updateStatus(newStatus: String) {
        status = newStatus
    }

    fun readSmsCount(context: Context) {

        updateStatus("Reading SMS...")

        val smsRepository = SmsRepository()

        val count = smsRepository.getSmsCount(context)

        updateStatus("Messages found: $count")
    }

    fun testReadSms(
        context: Context,
        includeContactNames: Boolean
    ) {
        try {

            val smsRepository = SmsRepository()

            val messages = smsRepository.getSmsMessages(
                context = context,
                includeContactNames = includeContactNames
            )

            updateStatus(
                "Read ${messages.size} SMS\nFirst: ${messages.firstOrNull()?.body}"
            )

        } catch (e: Exception) {

            updateStatus(
                "ERROR:\n${e.javaClass.simpleName}\n${e.message}"
            )
        }
    }
}