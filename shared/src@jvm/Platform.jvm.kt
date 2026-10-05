package kz.arctan.grepractice.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date

private val dataDir: File by lazy {
    File(System.getProperty("user.home"), ".grepractice").apply { mkdirs() }
}

actual fun readDataFile(name: String): String? =
    File(dataDir, name).takeIf { it.exists() }?.readText()

actual fun writeDataFile(name: String, content: String) {
    val tmp = File(dataDir, "$name.tmp")
    tmp.writeText(content)
    Files.move(tmp.toPath(), File(dataDir, name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

actual fun dataLocation(): String = dataDir.absolutePath

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatDateTime(millis: Long): String = SimpleDateFormat("MMM d, yyyy HH:mm").format(Date(millis))
