package com.lighttube.app.clean

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest

object DuplicateScanner {
    data class Match(val original: String, val duplicate: DocumentFile)
    data class Scan(val matches: List<Match>, val skipped: Int)

    fun scan(context: Context, tree: Uri): Scan {
        val folder = checkNotNull(DocumentFile.fromTreeUri(context, tree))
        val photos = folder.listFiles()
            .filter { it.isFile && it.type.orEmpty().startsWith("image/") }
            .sortedBy { it.name.orEmpty().lowercase() }
        var skipped = (photos.size - 400).coerceAtLeast(0)
        val seen = mutableMapOf<String, String>()
        val matches = mutableListOf<Match>()
        val buffer = ByteArray(64 * 1024)
        for (photo in photos.take(400)) {
            if (photo.length() > 50L * 1024 * 1024) {
                skipped++
                continue
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val key = runCatching {
                val stream = checkNotNull(context.contentResolver.openInputStream(photo.uri))
                stream.use { input ->
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 50L * 1024 * 1024)
                        digest.update(buffer, 0, count)
                    }
                    check(total > 0)
                }
                android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP)
            }.getOrNull()
            if (key == null) {
                skipped++
                continue
            }
            val original = seen[key]
            if (original == null) seen[key] = photo.name ?: "Photo"
            if (original != null) matches.add(Match(original, photo))
        }
        return Scan(matches, skipped)
    }
}
