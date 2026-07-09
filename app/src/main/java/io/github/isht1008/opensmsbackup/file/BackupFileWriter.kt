package io.github.isht1008.opensmsbackup.file

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupFileWriter(
    private val context: Context
) {

    fun write(json: JSONObject): File {

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss",
            Locale.getDefault()
        ).format(Date())

        val filename =
            "OpenSMSBackup_$timestamp.json"

        val documentsDir = context.getExternalFilesDir(
            Environment.DIRECTORY_DOCUMENTS
        ) ?: throw IllegalStateException("Documents directory unavailable")

        val file = File(
            documentsDir,
            filename
        )

        file.writeText(
            json.toString(2)
        )

        return file
    }
}