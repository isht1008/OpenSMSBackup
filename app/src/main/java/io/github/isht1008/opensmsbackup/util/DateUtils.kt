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
    fun formatIsoDate(
        isoDate: String
    ): String {

        return try {

            val instant = java.time.Instant.parse(isoDate)

            val formatter =
                java.time.format.DateTimeFormatter
                    .ofPattern("dd MMM yyyy, hh:mm a")
                    .withZone(
                        java.time.ZoneId.systemDefault()
                    )

            formatter.format(instant)

        } catch (e: Exception) {

            isoDate

        }
    }
}