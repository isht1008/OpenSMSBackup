package io.github.isht1008.opensmsbackup.backup

import android.content.Context
import io.github.isht1008.opensmsbackup.sms.SmsRepository

class BackupManager {

    private val smsRepository = SmsRepository()

    fun readAllMessages(
        context: Context,
        includeContactNames: Boolean
    ) = smsRepository.getSmsMessages(
        context = context,
        includeContactNames = includeContactNames
    )
}