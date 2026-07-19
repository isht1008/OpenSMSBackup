package io.github.isht1008.opensmsbackup.file

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.JsonWriter
import io.github.isht1008.opensmsbackup.model.BackupFileInfo
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupFileWriter(
    private val context: Context
) {

    fun write(writeJson: (JsonWriter) -> Unit): BackupFileInfo {

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss",
            Locale.getDefault()
        ).format(Date())

        val filename =
            "OpenSMSBackup_$timestamp.json"

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
            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            contentValues
        )
            ?: throw IllegalStateException(
                "Unable to create backup file"
            )

        val counter = CountingOutputStream(
            context.contentResolver.openOutputStream(uri)
                ?: run {
                    context.contentResolver.delete(uri, null, null)
                    throw IllegalStateException("Unable to open backup stream")
                }
        )
        try {
            counter.use { outputStream ->
                JsonWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                    writer.setIndent("  ")
                    writeJson(writer)
                }
            }
            val updated = context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) },
                null,
                null
            )
            check(updated == 1) { "Unable to finalize backup file" }
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }

        return BackupFileInfo(
            uri = uri.toString(),
            filename = filename,
            fileSizeBytes = counter.byteCount
        )
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var byteCount: Long = 0
            private set

        override fun write(value: Int) {
            out.write(value)
            byteCount++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            byteCount += length
        }
    }
}
