package com.lighttube.app.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.lighttube.app.network.MediaLinks
import java.util.UUID

class DownloadQueue(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    data class Item(
        val id: Long,
        val title: String,
        val source: String,
        val status: Int,
        val reason: Int,
        val done: Long,
        val total: Long,
    ) {
        val progress: Int? get() = if (total > 0) (done.toDouble() / total * 100).toInt().coerceIn(0, 100) else null
    }

    fun enqueue(link: Uri): Long {
        val uri = requireNotNull(MediaLinks.https(link.toString()))
        require(MediaLinks.isFile(uri))
        val title = uri.lastPathSegment.orEmpty().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val extension = title.substringAfterLast('.')
        val storedName = title.substringBeforeLast('.').take(80).ifBlank { "media" } +
            "-" + UUID.randomUUID() + "." + extension
        return manager.enqueue(
            DownloadManager.Request(uri)
                .setTitle(title)
                .setDescription("LightTube")
                .setMimeType(MediaLinks.mime(uri))
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, storedName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED),
        )
    }

    fun items(): List<Item> = manager.query(DownloadManager.Query())?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
        val title = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
        val source = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
        val status = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        val reason = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
        val done = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val total = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        buildList {
            while (cursor.moveToNext()) {
                add(Item(cursor.getLong(id), cursor.getString(title).orEmpty(),
                    cursor.getString(source).orEmpty(), cursor.getInt(status),
                    cursor.getInt(reason), cursor.getLong(done), cursor.getLong(total)))
            }
        }
    }.orEmpty()

    fun open(id: Long): Uri? = manager.getUriForDownloadedFile(id)

    fun remove(id: Long): Boolean = manager.remove(id) > 0
}
