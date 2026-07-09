package io.github.isht1008.opensmsbackup.util

import kotlin.math.ln
import kotlin.math.pow

object FileSizeUtils {

    fun format(bytes: Long): String {

        if (bytes <= 0) {
            return "0 B"
        }

        val units = arrayOf("B", "KB", "MB", "GB")

        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()

        val value =
            bytes / 1024.0.pow(digitGroups.toDouble())

        return "%.1f %s".format(
            value,
            units[digitGroups]
        )
    }
}