package io.github.isht1008.opensmsbackup.file

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.isht1008.opensmsbackup.model.BackupFileInfo

class BackupFileWriter(
    private val context: Context
) {

    fun write(json: JSONObject): BackupFileInfo {

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss",
            Locale.getDefault()
        ).format(Date())

        val filename =
            "OpenSMSBackup_$timestamp.json"

        val jsonText = json.toString(2)

        val contentValues = ContentValues().apply {
            put(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                filename
            )

            put(
                MediaStore.Files.FileColumns.MIME_TYPE,
                "application/json"
            )

            put(
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS +
                        "/OpenSMSBackup"
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            contentValues
        )
            ?: throw IllegalStateException(
                "Unable to create backup file"
            )

        context.contentResolver.openOutputStream(uri)
            ?.use { outputStream ->

                outputStream.write(
                    jsonText.toByteArray(Charsets.UTF_8)
                )

            }
            ?: throw IllegalStateException(
                "Unable to open backup stream"
            )

        return BackupFileInfo(
            uri = uri.toString(),
            filename = filename,
            fileSizeBytes = jsonText.toByteArray(Charsets.UTF_8).size.toLong()
        )
    }
}
