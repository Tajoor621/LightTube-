package com.lighttube.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.lighttube.app.network.MediaLinks

class DeepLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forwarded = Intent(this, ExploreActivity::class.java)
        val incoming = intent
        val link = runCatching { when (incoming.action) {
            Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT)?.let(MediaLinks::fromText)
            Intent.ACTION_VIEW -> incoming.data?.takeIf {
                it.scheme == "lighttube" && it.host == "open" && it.path.isNullOrEmpty()
            }?.getQueryParameter("url")?.let(MediaLinks::https)
            else -> null
        } }.getOrNull()
        if (link != null) {
            forwarded.data = link
        } else {
            val uris = runCatching { when (incoming.action) {
                Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java))
                Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else -> emptyList()
            } }.getOrDefault(emptyList()).asSequence()
                .filter { it.scheme == "content" }.distinct().take(30).toList()
            if (uris.isEmpty()) {
                Toast.makeText(this, "Share an HTTPS link or a media file.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            val clip = ClipData.newRawUri("Shared media", uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            forwarded.clipData = clip
            forwarded.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(forwarded)
        } catch (_: SecurityException) {
            Toast.makeText(this, "The sender did not grant access to the selected media.", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
