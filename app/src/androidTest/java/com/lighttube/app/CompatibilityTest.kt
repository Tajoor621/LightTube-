package com.lighttube.app

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lighttube.app.player.PlayerService
import com.lighttube.app.player.VideoPlaybackActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityTest {
    @Test
    fun launcherAndDownloadSearchSurviveRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val launcher = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<ExploreActivity>(launcher).use { scenario ->
            scenario.onActivity { activity ->
                val views = descendants(activity.findViewById(android.R.id.content))
                assertTrue(views.filterIsInstance<TextView>().any { it.text == "LightTube" })
                views.filterIsInstance<Button>().first { it.text == "Downloads" }.performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val views = descendants(activity.findViewById(android.R.id.content))
                assertTrue(views.filterIsInstance<Button>().any { it.text == "Search or refresh" })
            }
        }
    }

    @Test
    fun directLinkOptionsSurviveRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val link = Intent(context, ExploreActivity::class.java)
            .setData(android.net.Uri.parse("https://example.com/clip.mp4"))
        ActivityScenario.launch<ExploreActivity>(link).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                val buttons = descendants(activity.findViewById(android.R.id.content)).filterIsInstance<Button>()
                assertTrue(buttons.any { it.text == "Stream in LightTube" })
                assertTrue(buttons.any { it.text == "Download original file" })
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun localAudioStartsInTheMediaSession() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "LightTube test.wav")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            val samples = 8_000 * 20 * 2
            val wav = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(36 + samples)
            wav.put("WAVEfmt ".toByteArray()).putInt(16)
            wav.putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            wav.put("data".toByteArray()).putInt(samples)
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(wav.array()) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val player = Intent(context, VideoPlaybackActivity::class.java)
                .setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ActivityScenario.launch<VideoPlaybackActivity>(player).use {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
                val future = MediaController.Builder(context, token).buildAsync()
                try {
                    val controller = future.get(15, TimeUnit.SECONDS)
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    var playing = false
                    while (!playing && System.nanoTime() < deadline) {
                        instrumentation.runOnMainSync {
                            playing = controller.playbackState == Player.STATE_READY && controller.isPlaying
                        }
                        if (!playing) Thread.sleep(100)
                    }
                    assertTrue("The session should play the local WAV", playing)
                    instrumentation.runOnMainSync {
                        assertEquals(uri, controller.currentMediaItem?.requestMetadata?.mediaUri)
                        controller.stop()
                    }
                } finally {
                    instrumentation.runOnMainSync { MediaController.releaseFuture(future) }
                }
            }
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    private fun descendants(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) {
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        } else emptyList()
}
