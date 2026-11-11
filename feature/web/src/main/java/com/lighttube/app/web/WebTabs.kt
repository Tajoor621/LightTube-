package com.lighttube.app.web

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.lighttube.app.network.MediaLinks
import org.json.JSONArray

class WebTabs(private val context: Context) {
    private val storage = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    fun open(uri: Uri) {
        val link = requireNotNull(MediaLinks.https(uri.toString()))
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, link)
    }

    fun bookmarks(): List<Uri> {
        val values = JSONArray(storage.getString("urls", "[]") ?: "[]")
        return (0 until values.length()).mapNotNull { MediaLinks.https(values.optString(it)) }
    }

    fun save(uri: Uri) = store((listOf(uri) + bookmarks()).distinct().take(100))

    fun remove(uri: Uri) = store(bookmarks() - uri)

    private fun store(values: List<Uri>) {
        storage.edit().putString("urls", JSONArray(values.map(Uri::toString)).toString()).apply()
    }
}
