package com.lighttube.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

object Documents {
    fun name(resolver: ContentResolver, uri: Uri): String {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.take(150)?.takeIf { it.isNotBlank() } ?: "Media"
    }

    fun copy(resolver: ContentResolver, source: Uri, destination: Uri) {
        checkNotNull(resolver.openInputStream(source)).use { input ->
            checkNotNull(resolver.openOutputStream(destination, "w")).use { output ->
                input.copyTo(output)
            }
        }
    }
}
