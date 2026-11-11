package com.lighttube.app.vault

import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.concurrent.Executors

class VaultActivity : AppCompatActivity() {
    private lateinit var rows: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()
    private var unlocked = false
    private var outsideForResult = false
    private var busy = false
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null
    private var clearPreview: (() -> Unit)? = null
    private var incoming = emptyList<Uri>()

    private val unlockPrompt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        outsideForResult = false
        if (Build.VERSION.SDK_INT >= 23 && it.resultCode == RESULT_OK) {
            unlocked = true
            if (incoming.isEmpty()) refresh() else offerImport(incoming)
        } else lockScreen()
    }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        outsideForResult = false
        if (Build.VERSION.SDK_INT >= 23 && unlocked && uris.isNotEmpty()) offerImport(uris.take(30))
        else if (!unlocked) lockScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        incoming = intent.clipData?.let { clips ->
            (0 until minOf(30, clips.itemCount)).mapNotNull { clips.getItemAt(it).uri }
        }.orEmpty()
        if (savedInstanceState?.getBoolean("importHandled") == true) incoming = emptyList()
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = ScrollView(this).apply { addView(rows) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            val space = (24 * resources.displayMetrics.density).toInt()
            view.setPadding(bars.left + space, bars.top + space, bars.right + space, bars.bottom + space)
            windowInsets
        }
        setContentView(root)
        val directory = File(cacheDir, "vault-preview")
        val stalePreviews = directory.listFiles().orEmpty()
        worker.execute { stalePreviews.forEach { it.delete() } }
        lockScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("importHandled", incoming.isEmpty())
    }

    @Suppress("DEPRECATION")
    private fun lockScreen() {
        unlocked = false
        rows.removeAllViews()
        title("Private vault")
        if (Build.VERSION.SDK_INT < 23) {
            title("The encrypted vault requires Android 6 or later.")
            return
        }
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            title("Set a PIN, pattern or password in Android settings to use the vault.")
            return
        }
        button("Unlock with device credentials") {
            val challenge = keyguard.createConfirmDeviceCredentialIntent("Unlock LightTube vault", "Confirm your screen lock")
            if (challenge == null) message("Device credentials are unavailable.")
            else {
                outsideForResult = true
                unlockPrompt.launch(challenge)
            }
        }
    }

    @RequiresApi(23)
    private fun refresh(query: String = "") {
        if (!unlocked) return
        rows.removeAllViews()
        title("Private vault")
        button("Lock vault") { lockScreen() }
        button("Import media") {
            outsideForResult = true
            picker.launch(arrayOf("image/*", "video/*"))
        }
        val search = EditText(this).apply {
            hint = "Search vault"
            setSingleLine()
            setText(query)
        }
        rows.addView(search)
        button("Search") { refresh(search.text.toString()) }
        perform({ VaultStore(this).entries() }) { entries ->
            val visible = entries.filter { it.label.contains(query, ignoreCase = true) }
            if (visible.isEmpty()) title("No matching media.")
            visible.forEach { entry ->
                button(entry.label) {
                    showDialog(MaterialAlertDialogBuilder(this).setTitle(entry.label)
                        .setItems(arrayOf("Preview", "Delete")) { _, choice ->
                            if (choice == 0) openPreview(entry) else confirmDelete(entry)
                        })
                }
            }
        }
    }

    @RequiresApi(23)
    private fun offerImport(uris: List<Uri>) {
        incoming = emptyList()
        showDialog(MaterialAlertDialogBuilder(this).setTitle("Copy ${uris.size} files into the vault?")
            .setMessage("Original files stay where they are. The limit is 32 MB per file.")
            .setNegativeButton("Cancel") { _, _ -> refresh() }
            .setOnCancelListener { refresh() }
            .setPositiveButton("Copy") { _, _ ->
                perform({
                    val vault = VaultStore(this)
                    uris.count { runCatching { vault.import(it) }.isSuccess }
                }) { imported ->
                    message("Imported $imported of ${uris.size} files.")
                    refresh()
                }
            })
    }

    @RequiresApi(23)
    private fun confirmDelete(entry: VaultStore.Entry) {
        showDialog(MaterialAlertDialogBuilder(this).setTitle("Delete vault copy?")
            .setMessage(entry.label)
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ ->
                perform({ VaultStore(this).remove(entry) }) {
                    if (!it) message("The copy could not be deleted.")
                    refresh()
                }
            })
    }

    @RequiresApi(23)
    private fun openPreview(entry: VaultStore.Entry) {
        perform({
            val dir = File(cacheDir, "vault-preview").apply { mkdirs() }
            val file = File.createTempFile("preview-", ".media", dir)
            try {
                file.outputStream().use { VaultStore(this).open(entry, it) }
                file
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }, discard = { it.delete() }) { file ->
            val video = if (entry.mime.startsWith("video/")) VideoView(this) else null
            val view = video ?: ImageView(this).apply {
                adjustViewBounds = true
                contentDescription = entry.label
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                var scale = 1
                while (maxOf(options.outWidth, options.outHeight) / scale > 1600) scale *= 2
                options.inJustDecodeBounds = false
                options.inSampleSize = scale
                setImageBitmap(BitmapFactory.decodeFile(file.path, options))
            }
            val frame = android.widget.FrameLayout(this).apply {
                val height = (320 * resources.displayMetrics.density).toInt()
                addView(view, android.widget.FrameLayout.LayoutParams(-1, height))
            }
            clearPreview = {
                video?.stopPlayback()
                file.delete()
            }
            showDialog(MaterialAlertDialogBuilder(this).setTitle(entry.label)
                .setView(frame).setPositiveButton("Close", null)) {
                clearPreview?.invoke()
                clearPreview = null
            }
            video?.apply {
                setMediaController(MediaController(this@VaultActivity).also { it.setAnchorView(this) })
                setVideoURI(Uri.fromFile(file))
                setOnErrorListener { _, _, _ -> message("This video could not be played."); true }
                start()
            }
        }
    }

    private fun showDialog(builder: MaterialAlertDialogBuilder, dismissed: () -> Unit = {}) {
        val dialog = builder.create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
            dismissed()
        }
        activeDialog = dialog
        dialog.show()
    }

    private fun <T> perform(work: () -> T, discard: (T) -> Unit = {}, complete: (T) -> Unit) {
        if (!unlocked || busy) return
        busy = true
        worker.execute {
            val result = runCatching(work)
            runOnUiThread {
                busy = false
                if (isDestroyed || isFinishing || !unlocked) result.onSuccess(discard)
                else result.fold(complete) { message("The vault operation could not be completed.") }
            }
        }
    }

    private fun title(label: String) {
        rows.addView(TextView(this).apply { text = label; textSize = 20f })
    }

    private fun button(label: String, action: () -> Unit) {
        rows.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { if (!busy) action() }
        })
    }

    private fun message(label: String) = Toast.makeText(this, label, Toast.LENGTH_SHORT).show()

    override fun onStop() {
        activeDialog?.dismiss()
        if (!outsideForResult) lockScreen()
        super.onStop()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context, media: List<Uri>) {
            val intent = Intent(context, VaultActivity::class.java)
            if (media.isNotEmpty()) {
                intent.clipData = ClipData.newRawUri("Media for vault", media.first()).also { clip ->
                    media.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }
}
