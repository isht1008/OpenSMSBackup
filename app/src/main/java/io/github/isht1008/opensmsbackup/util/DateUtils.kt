package io.github.isht1008.opensmsbackup.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    fun formatDateIST(timestamp: Long): String {
        val formatter = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        )

        formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")

        return formatter.format(Date(timestamp))
    }
}