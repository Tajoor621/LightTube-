package com.lighttube.app.vault

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.lighttube.app.data.Documents
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class VaultStore(private val context: Context) {
    private val root = File(context.noBackupFilesDir, "vault").apply { mkdirs() }

    data class Entry(val file: File, val label: String, val mime: String)

    fun entries(): List<Entry> = root.listFiles().orEmpty()
        .filter { it.extension == "ltv" }
        .sortedByDescending { it.lastModified() }
        .mapNotNull { file ->
            runCatching { file.inputStream().use { readHeader(DataInputStream(it), file) } }.getOrNull()
        }

    fun import(uri: Uri) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        require(mime.startsWith("image/") || mime.startsWith("video/"))
        val id = UUID.randomUUID().toString()
        val destination = File(root, "$id.ltv")
        val staging = File(root, "$id.part")
        val header = (mime + "\n" + Documents.name(resolver, uri)).toByteArray(Charsets.UTF_8)
        val nameCipher = encrypt("$id/header")
        val encryptedHeader = nameCipher.doFinal(header)
        val body = encrypt("$id/body")
        try {
            staging.outputStream().buffered().use { output ->
                output.write(nameCipher.iv)
                output.write(encryptedHeader.size ushr 8)
                output.write(encryptedHeader.size and 255)
                output.write(encryptedHeader)
                output.write(body.iv)
                checkNotNull(resolver.openInputStream(uri)).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= MAX_BYTES) { "Vault import limit is 32 MB per file." }
                        body.update(buffer, 0, count)?.let(output::write)
                    }
                    require(size > 0)
                    output.write(body.doFinal())
                }
            }
            check(staging.renameTo(destination))
        } finally {
            staging.delete()
        }
    }

    fun open(entry: Entry, output: OutputStream) {
        DataInputStream(entry.file.inputStream().buffered()).use { input ->
            readHeader(input, entry.file)
            val nonce = ByteArray(12).also(input::readFully)
            val cipher = decrypt(nonce, "${entry.file.nameWithoutExtension}/body")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                cipher.update(buffer, 0, count)?.let(output::write)
            }
            output.write(cipher.doFinal())
        }
    }

    fun remove(entry: Entry): Boolean = entry.file.delete()

    private fun readHeader(input: DataInputStream, file: File): Entry {
        val nonce = ByteArray(12).also(input::readFully)
        val length = input.readUnsignedShort()
        require(length in 17..4096)
        val encrypted = ByteArray(length).also(input::readFully)
        val cipher = decrypt(nonce, "${file.nameWithoutExtension}/header")
        val values = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        return Entry(file, values.substringAfter('\n'), values.substringBefore('\n'))
    }

    private fun encrypt(aad: String): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, key())
        check(iv.size == 12)
        updateAAD(aad.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(nonce: ByteArray, aad: String): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        updateAAD(aad.toByteArray(Charsets.UTF_8))
    }

    private fun key(): SecretKey = synchronized(VaultStore::class.java) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null)
        if (existing is SecretKey) return@synchronized existing
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "lighttube-vault-v1"
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}
