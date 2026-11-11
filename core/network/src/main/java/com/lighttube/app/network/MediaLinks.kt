package com.lighttube.app.network

import android.net.Uri
import java.net.URI
import java.util.Locale

object MediaLinks {
    private val sharedLink = Regex("""https://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val formats = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm",
        "mov" to "video/quicktime", "mkv" to "video/x-matroska", "ts" to "video/mp2t",
        "3gp" to "video/3gpp", "flv" to "video/x-flv", "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4", "aac" to "audio/aac", "ogg" to "audio/ogg",
        "opus" to "audio/ogg", "wav" to "audio/wav", "flac" to "audio/flac",
        "m3u8" to "application/x-mpegURL", "mpd" to "application/dash+xml",
    )

    fun https(text: String): Uri? {
        val value = runCatching { URI(text.trim()) }.getOrNull() ?: return null
        if (!value.scheme.equals("https", true) || value.host.isNullOrBlank() ||
            value.rawUserInfo != null || value.port !in -1..65535
        ) return null
        return Uri.parse(value.toASCIIString()).normalizeScheme()
    }

    fun fromText(text: String): Uri? = https(text) ?: sharedLink.findAll(text.take(16_384))
        .mapNotNull { https(it.value.trimEnd('.', ',', ')', ']', '}')) }
        .firstOrNull()

    fun mime(uri: Uri): String? =
        formats[uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)]

    fun isFile(uri: Uri): Boolean =
        mime(uri)?.let { it.startsWith("audio/") || it.startsWith("video/") } == true
}
