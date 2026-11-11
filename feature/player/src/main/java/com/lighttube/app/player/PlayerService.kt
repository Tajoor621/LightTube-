package com.lighttube.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lighttube.app.network.MediaLinks
import com.lighttube.app.ui.R as UiResources

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val notifications = DefaultMediaNotificationProvider.Builder(this).build()
        notifications.setSmallIcon(UiResources.drawable.ic_lighttube_notification)
        setMediaNotificationProvider(notifications)
        val player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        val launch = Intent(this, VideoPlaybackActivity::class.java)
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> {
                    if (controller.uid != Process.myUid()) return Futures.immediateFuture(emptyList())
                    val resolved = mediaItems.mapNotNull { item ->
                        val uri = item.requestMetadata.mediaUri ?: return@mapNotNull null
                        if (uri.scheme != "content" && MediaLinks.https(uri.toString()) == null) {
                            return@mapNotNull null
                        }
                        item.buildUpon().setUri(uri).apply {
                            MediaLinks.mime(uri)?.let { setMimeType(it) }
                        }.build()
                    }
                    return Futures.immediateFuture(resolved)
                }
            })
            .setSessionActivity(PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
