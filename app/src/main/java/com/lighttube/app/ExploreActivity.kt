package com.lighttube.app

import android.Manifest
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.Formatter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lighttube.app.boost.DeviceHealth
import com.lighttube.app.clean.DuplicateScanner
import com.lighttube.app.data.Documents
import com.lighttube.app.databinding.ActivityExploreBinding
import com.lighttube.app.databinding.ViewLighttubeBrandBinding
import com.lighttube.app.download.DownloadQueue
import com.lighttube.app.network.MediaLinks
import com.lighttube.app.player.VideoPlaybackActivity
import com.lighttube.app.vault.VaultActivity
import com.lighttube.app.web.WebTabs
import com.lighttube.app.whatsapp.StatusFolder
import java.util.concurrent.Executors

class ExploreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExploreBinding
    private val downloads by lazy { DownloadQueue(this) }
    private val tabs by lazy { WebTabs(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private var exportUri: Uri? = null
    private var screenLink: Uri? = null
    private var showingSharedMedia = false
    private var scanGeneration = 0
    private var queueFilter = ""
    private var showQueue = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            VideoPlaybackActivity.open(this, uri)
        }
    }
    private val statusPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) openStatuses(uri)
    }
    private val statusFilesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            statuses()
            uris.take(30).forEach { uri ->
                val mime = runCatching { contentResolver.getType(uri) }.getOrNull().orEmpty()
                if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    showStatus(StatusFolder.Item(Documents.name(contentResolver, uri), uri, mime))
                }
            }
        }
    }
    private val cleanerPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scanDuplicates(uri)
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = exportUri
        val destination = result.data?.data
        exportUri = null
        if (source != null && destination != null && result.resultCode == RESULT_OK) {
            if (source == destination) {
                message("Choose a different file.")
            } else {
                runWork({ Documents.copy(contentResolver, source, destination) }) { message("File saved.") }
            }
        }
    }
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message(if (granted) "Notifications enabled." else "Notifications are disabled.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = prefs.getInt("mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (delegate.localNightMode != mode) delegate.localNightMode = mode
        binding = ActivityExploreBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }
        setContentView(binding.root)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = home()
        }
        backCallback = callback
        onBackPressedDispatcher.addCallback(this, callback)
        exportUri = savedInstanceState?.getString("exportUri")?.let(Uri::parse)
        when {
            savedInstanceState == null -> { home(); handleIncoming(intent) }
            savedInstanceState.getBoolean("downloadsVisible") -> queue(savedInstanceState.getString("queueFilter").orEmpty())
            savedInstanceState.getString("screenLink") != null ->
                savedInstanceState.getString("screenLink")?.let(MediaLinks::https)?.let(::showLink) ?: home()
            savedInstanceState.getBoolean("sharedMediaVisible") -> { home(); handleIncoming(intent) }
            else -> home()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("exportUri", exportUri?.toString())
        outState.putString("screenLink", screenLink?.toString())
        outState.putBoolean("sharedMediaVisible", showingSharedMedia)
        outState.putBoolean("downloadsVisible", showQueue)
        outState.putString("queueFilter", queueFilter)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent) {
        intent.data?.toString()?.let(MediaLinks::https)?.let { showLink(it); return }
        val clips = intent.clipData ?: return
        val sources = (0 until minOf(clips.itemCount, 30)).mapNotNull { clips.getItemAt(it).uri }
        if (sources.isEmpty()) return
        screen("Shared media")
        showingSharedMedia = true
        sources.forEach { uri ->
            val mime = runCatching { contentResolver.getType(uri) }.getOrNull().orEmpty()
            val name = Documents.name(contentResolver, uri)
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                button("Play $name") { VideoPlaybackActivity.open(this, uri) }
            }
        }
        val vaultMedia = sources.filter {
            val mime = runCatching { contentResolver.getType(it) }.getOrNull().orEmpty()
            mime.startsWith("image/") || mime.startsWith("video/")
        }
        if (vaultMedia.isNotEmpty()) button("Import photos and videos to vault") {
            VaultActivity.launch(this, vaultMedia)
        } else paragraph("Audio files can be opened in the player.")
    }

    private fun home() {
        screen(getString(R.string.home_title), isHome = true)
        paragraph(getString(R.string.home_description))
        val input = field("Paste an HTTPS link", uri = true)
        button("Open link") { parseInput(input.text.toString()) }
        button("Use clipboard link") {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
            parseInput(value?.toString().orEmpty())
        }
        heading("Your media")
        button("Downloads") { queue("") }
        button("Play a local file") { attempt { filePicker.launch(arrayOf("audio/*", "video/*")) } }
        button("Private vault") { VaultActivity.launch(this, emptyList()) }
        heading("Utilities")
        button("WhatsApp statuses") { statuses() }
        button("Bookmarks") { bookmarks() }
        button("Photo cleaner") { cleaner() }
        button("Battery and memory") { deviceHealth() }
        button("Settings") { settings() }
    }

    private fun parseInput(text: String) {
        val link = MediaLinks.fromText(text)
        if (link == null) message("Enter a valid HTTPS link.") else showLink(link)
    }

    private fun showLink(uri: Uri) {
        screen("Open link")
        screenLink = uri
        paragraph(uri.toString())
        val mime = MediaLinks.mime(uri)
        if (mime != null) {
            button("Stream in LightTube") { VideoPlaybackActivity.open(this, uri) }
            if (MediaLinks.isFile(uri)) {
                button("Download original file") {
                    runWork({ downloads.enqueue(uri) }) { queue("") }
                }
            }
        } else paragraph("This link opens as a web page. To download, use a direct media file URL.")
        button("Open in browser tab") { attempt { tabs.open(uri) } }
        button("Save bookmark") { tabs.save(uri); message("Bookmark saved.") }
    }

    private fun queue(filter: String) {
        screen("Downloads")
        showQueue = true
        queueFilter = filter
        val input = field("Search download names")
        input.setText(filter)
        button("Search or refresh") { queue(input.text.toString().trim()) }
        val generation = scanGeneration
        runWork({ downloads.items() }) { items ->
            if (!showQueue || generation != scanGeneration) return@runWork
            val visible = items.filter { it.title.contains(queueFilter, ignoreCase = true) }
            if (visible.isEmpty()) paragraph("No matching downloads.")
            for (item in visible) {
                val state = when (item.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Ready"
                    DownloadManager.STATUS_RUNNING -> item.progress?.let { "$it%" } ?: "Downloading"
                    DownloadManager.STATUS_PENDING -> "Queued"
                    DownloadManager.STATUS_PAUSED -> "Waiting for Android"
                    DownloadManager.STATUS_FAILED -> "Failed (${item.reason})"
                    else -> "Unknown status"
                }
                button("${item.title} · $state") { downloadActions(item) }
            }
        }
    }

    private fun downloadActions(item: DownloadQueue.Item) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
            actions += "Play" to {
                val uri = downloads.open(item.id)
                if (uri == null) message("The downloaded file is unavailable.") else VideoPlaybackActivity.open(this, uri)
            }
            actions += "Save a copy" to {
                val uri = downloads.open(item.id)
                if (uri == null) message("The downloaded file is unavailable.")
                else export(uri, item.title, contentResolver.getType(uri) ?: "application/octet-stream")
            }
        }
        if (item.status == DownloadManager.STATUS_FAILED) {
            actions += "Retry" to {
                val uri = MediaLinks.https(item.source)
                if (uri == null) message("The source link is unavailable.")
                else runWork({ downloads.enqueue(uri) }) { queue(queueFilter) }
            }
        }
        val active = item.status != DownloadManager.STATUS_SUCCESSFUL
        actions += (if (active) "Cancel or remove" else "Delete") to {
            MaterialAlertDialogBuilder(this).setTitle("Remove download?")
                .setMessage(item.title)
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove") { _, _ ->
                    runWork({ downloads.remove(item.id) }) { removed ->
                        if (!removed) message("The download was already removed.")
                        queue(queueFilter)
                    }
                }.show()
        }
        MaterialAlertDialogBuilder(this).setTitle(item.title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> attempt(actions[index].second) }.show()
    }

    private fun bookmarks() {
        screen("Bookmarks")
        val link = field("HTTPS address", uri = true)
        button("Add bookmark") {
            val uri = MediaLinks.https(link.text.toString())
            if (uri == null) message("Enter a valid HTTPS link.") else { tabs.save(uri); bookmarks() }
        }
        val entries = tabs.bookmarks()
        if (entries.isEmpty()) paragraph("No saved bookmarks.")
        entries.forEach { uri ->
            button(uri.toString()) {
                MaterialAlertDialogBuilder(this).setTitle(uri.host)
                    .setItems(arrayOf("Open", "Remove")) { _, i ->
                        if (i == 0) attempt { tabs.open(uri) } else { tabs.remove(uri); bookmarks() }
                    }.show()
            }
        }
    }

    private fun statuses() {
        screen("WhatsApp statuses")
        paragraph("Choose .Statuses or the WhatsApp Media folder. If your device hides .Statuses, you can pick a saved image or video directly.")
        button("Choose folder") { attempt { statusPicker.launch(null) } }
        button("Choose saved status files") { attempt { statusFilesPicker.launch(arrayOf("image/*", "video/*")) } }
    }

    private fun openStatuses(tree: Uri) {
        statuses()
        val generation = scanGeneration
        runWork({ StatusFolder.list(this, tree) }) { items ->
            if (generation != scanGeneration) return@runWork
            if (items.isEmpty()) paragraph("No images or videos are available in this folder.")
            items.forEach(::showStatus)
        }
    }

    private fun showStatus(item: StatusFolder.Item) {
        button(item.name) {
            MaterialAlertDialogBuilder(this).setTitle(item.name)
                .setItems(arrayOf("Save a copy", "Copy to vault")) { _, which ->
                    if (which == 0) export(item.uri, item.name, item.mime)
                    else VaultActivity.launch(this, listOf(item.uri))
                }.show()
        }
    }

    private fun export(uri: Uri, name: String, mime: String) {
        exportUri = uri
        attempt {
            exportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name))
        }
    }

    private fun cleaner() {
        screen("Photo cleaner")
        paragraph("Scan one folder for byte-identical photos. You choose which copies to delete.")
        button("Choose folder to scan") { attempt { cleanerPicker.launch(null) } }
    }

    private fun scanDuplicates(tree: Uri) {
        cleaner()
        val generation = scanGeneration
        val status = paragraph("Scanning photos…")
        runWork({ DuplicateScanner.scan(this, tree) }, onError = { status.text = "Could not scan this folder." }) { result ->
            if (generation != scanGeneration) return@runWork
            status.text = "${result.matches.size} duplicate copies found. ${result.skipped} photos skipped."
            if (result.matches.isEmpty()) return@runWork
            button("Review duplicate copies") {
                val selected = BooleanArray(result.matches.size)
                val choices = result.matches.map {
                    "${it.duplicate.name ?: "Photo"} · keep ${it.original}"
                }.toTypedArray()
                MaterialAlertDialogBuilder(this).setTitle("Select copies to delete")
                    .setMultiChoiceItems(choices, selected) { _, index, checked -> selected[index] = checked }
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue") { _, _ ->
                        val files = result.matches.filterIndexed { index, _ -> selected[index] }
                        if (files.isEmpty()) return@setPositiveButton
                        MaterialAlertDialogBuilder(this).setTitle("Delete ${files.size} selected copies?")
                            .setNegativeButton("Keep", null)
                            .setPositiveButton("Delete") { _, _ ->
                                runWork({
                                    val freshMatches = DuplicateScanner.scan(this, tree).matches.associateBy { it.duplicate.uri }
                                    files.count { selectedFile ->
                                        val current = freshMatches[selectedFile.duplicate.uri]
                                        current != null && current.original == selectedFile.original &&
                                            runCatching { current.duplicate.delete() }.getOrDefault(false)
                                    }
                                }) { count ->
                                    message("Deleted $count of ${files.size} selected copies.")
                                    scanDuplicates(tree)
                                }
                            }.show()
                    }.show()
            }
        }
    }

    private fun deviceHealth() {
        screen("Battery and memory")
        val health = DeviceHealth.read(this)
        paragraph("Battery: ${health.battery?.let { "$it%" } ?: "Unavailable"}")
        paragraph("Battery saver: ${if (health.powerSaver) "On" else "Off"}")
        paragraph("Available memory: ${Formatter.formatShortFileSize(this, health.available)} of ${Formatter.formatShortFileSize(this, health.total)}")
        button("Battery saver settings") { attempt { startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) } }
    }

    private fun settings() {
        screen("Settings")
        showBrand()
        paragraph(getString(R.string.app_version, BuildConfig.VERSION_NAME))
        button("Appearance") {
            val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
            MaterialAlertDialogBuilder(this).setTitle("Appearance")
                .setSingleChoiceItems(arrayOf("System", "Light", "Dark"), modes.indexOf(delegate.localNightMode)) { dialog, i ->
                    prefs.edit().putInt("mode", modes[i]).apply()
                    dialog.dismiss()
                    delegate.localNightMode = modes[i]
                }.setNegativeButton("Cancel", null).show()
        }
        if (Build.VERSION.SDK_INT >= 33) button("Enable notifications") {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        paragraph("Downloads and bookmarks are stored on this device. Browser tabs use your chosen browser.")
    }

    private fun screen(title: String, isHome: Boolean = false) {
        scanGeneration++
        showQueue = false
        screenLink = null
        showingSharedMedia = false
        binding.content.removeAllViews()
        binding.root.scrollTo(0, 0)
        backCallback?.isEnabled = !isHome
        if (isHome) showBrand()
        else button("Home") { home() }
        heading(title, 30f)
    }

    private fun showBrand() {
        ViewLighttubeBrandBinding.inflate(layoutInflater, binding.content, true)
    }

    private var backCallback: OnBackPressedCallback? = null

    private fun heading(title: String, size: Float = 21f) = paragraph(title).apply {
        textSize = size
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        ViewCompat.setAccessibilityHeading(this, true)
    }

    private fun paragraph(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setPadding(0, 10, 0, 12)
        binding.content.addView(this)
    }

    private fun field(hintText: String, uri: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or if (uri) InputType.TYPE_TEXT_VARIATION_URI else 0
        setSingleLine()
        binding.content.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun button(label: String, action: () -> Unit) {
        binding.content.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun <T> runWork(work: () -> T, onError: (() -> Unit)? = null, complete: (T) -> Unit) {
        worker.execute {
            val result = runCatching(work)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) result.fold(complete) {
                    if (onError != null) onError() else message("The operation could not be completed.")
                }
            }
        }
    }

    private fun attempt(action: () -> Unit) {
        runCatching(action).onFailure { message("No compatible app or file provider is available.") }
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
