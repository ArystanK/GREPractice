package kz.arctan.grepractice.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

private lateinit var dataDir: File

/** Must be called from the Activity before the UI is shown. */
fun initStorage(context: Context) {
    dataDir = context.applicationContext.filesDir
}

actual fun readDataFile(name: String): String? =
    File(dataDir, name).takeIf { it.exists() }?.readText()

actual fun writeDataFile(name: String, content: String) {
    val tmp = File(dataDir, "$name.tmp")
    tmp.writeText(content)
    val target = File(dataDir, name)
    if (!tmp.renameTo(target)) {
        target.writeText(content)
        tmp.delete()
    }
}

actual fun readDataBytes(name: String): ByteArray? =
    File(dataDir, name).takeIf { it.exists() }?.readBytes()

actual fun writeDataBytes(name: String, bytes: ByteArray) {
    val target = File(dataDir, name).apply { parentFile?.mkdirs() }
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) {
        target.writeBytes(bytes)
        tmp.delete()
    }
}

actual fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

actual fun dataLocation(): String = dataDir.absolutePath

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatDateTime(millis: Long): String = SimpleDateFormat("MMM d, yyyy HH:mm").format(Date(millis))
