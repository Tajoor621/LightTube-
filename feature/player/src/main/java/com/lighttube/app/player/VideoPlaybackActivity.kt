package com.lighttube.app.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class VideoPlaybackActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var pip: Button
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingUri: Uri? = null
    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@VideoPlaybackActivity, "Could not play this media.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUri = if (savedInstanceState == null) intent.data
            else savedInstanceState.getString("pendingMedia")?.let(Uri::parse)
        playerView = PlayerView(this).apply { setShowSubtitleButton(true) }
        pip = Button(this).apply {
            text = "Picture in picture"
            isEnabled = canUsePip()
            setOnClickListener { enterPip() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pip)
            addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingMedia", pendingUri?.toString())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUri = intent.data
        controller?.let(::openPendingMedia)
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture !== future) return@addListener
            val active = runCatching { future.get() }.getOrNull()
            if (active != null) {
                controller = active
                active.addListener(listener)
                playerView.player = active
                openPendingMedia(active)
            } else Toast.makeText(this, "Could not start the player.", Toast.LENGTH_LONG).show()
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    private fun openPendingMedia(active: MediaController) {
        val uri = pendingUri ?: return
        pendingUri = null
        val item = MediaItem.Builder()
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
        active.setMediaItem(item)
        active.prepare()
        active.play()
    }

    private fun canUsePip(): Boolean = Build.VERSION.SDK_INT >= 26 &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && canUsePip() && controller?.isPlaying == true) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onPictureInPictureModeChanged(active: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(active, config)
        pip.visibility = if (active) android.view.View.GONE else android.view.View.VISIBLE
        playerView.useController = !active
    }

    override fun onStop() {
        playerView.player = null
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    companion object {
        fun open(context: Context, uri: Uri) {
            val intent = Intent(context, VideoPlaybackActivity::class.java).setData(uri)
            if (uri.scheme == "content") intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }
}
