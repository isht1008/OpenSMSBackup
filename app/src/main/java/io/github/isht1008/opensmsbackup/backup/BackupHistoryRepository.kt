package io.github.isht1008.opensmsbackup.backup

import android.content.Context
import android.provider.MediaStore
import io.github.isht1008.opensmsbackup.model.BackupHistoryItem
import org.json.JSONObject

class BackupHistoryRepository {

    fun getBackups(
        context: Context
    ): List<BackupHistoryItem> {

        val backups = mutableListOf<BackupHistoryItem>()

        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection =
            "${MediaStore.Files.FileColumns.RELATIVE_PATH}=?"

        val selectionArgs =
            arrayOf("Documents/OpenSMSBackup/")

        val sortOrder =
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns._ID
                )

            val nameIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.DISPLAY_NAME
                )

            val sizeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.SIZE
                )

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idIndex)

                val uri =
                    MediaStore.Files.getContentUri("external")
                        .buildUpon()
                        .appendPath(id.toString())
                        .build()

                val displayName =
                    cursor.getString(nameIndex)

                val size =
                    cursor.getLong(sizeIndex)

                var createdAt = ""
                var messages = 0
                var conversations = 0

                runCatching {

                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { reader ->

                            val json =
                                JSONObject(reader.readText())

                            createdAt =
                                json.optString("createdAt")

                            val statistics =
                                json.optJSONObject("statistics")

                            messages =
                                statistics?.optInt(
                                    "totalMessages"
                                ) ?: 0

                            conversations =
                                statistics?.optInt(
                                    "totalConversations"
                                ) ?: 0
                        }
                }

                backups.add(
                    BackupHistoryItem(
                        displayName = displayName,
                        uri = uri,
                        createdAt = createdAt,
                        messageCount = messages,
                        conversationCount = conversations,
                        fileSizeBytes = size
                    )
                )
            }
        }

        return backups
    }
}