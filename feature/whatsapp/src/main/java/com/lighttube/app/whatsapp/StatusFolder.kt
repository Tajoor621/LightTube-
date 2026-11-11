package com.lighttube.app.whatsapp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object StatusFolder {
    data class Item(val name: String, val uri: Uri, val mime: String)

    fun list(context: Context, tree: Uri): List<Item> {
        val root = checkNotNull(DocumentFile.fromTreeUri(context, tree))
        val folder = if (root.name == ".Statuses") root else root.findFile(".Statuses") ?: root
        return folder.listFiles().mapNotNull { file ->
            val mime = file.type.orEmpty()
            if (file.isFile && (mime.startsWith("image/") || mime.startsWith("video/"))) {
                Item(file.name.orEmpty().ifBlank { "Status" }, file.uri, mime)
            } else null
        }.sortedBy { it.name.lowercase() }
    }
}
