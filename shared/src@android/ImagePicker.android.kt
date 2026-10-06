package kz.arctan.grepractice.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kz.arctan.grepractice.data.MAX_IMAGE_BYTES
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberImagePicker(onResult: (Result<PickedImage?>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onResult(if (uri == null) Result.success(null) else runCatching { read(context, uri) })
    }
    return { launcher.launch("image/*") }
}

actual val canPasteImage: Boolean = false

actual fun pasteImageFromClipboard(): PickedImage? = null

private fun read(context: Context, uri: Uri): PickedImage {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("Couldn't open the image.")
    val ext = when (context.contentResolver.getType(uri)) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> throw IllegalArgumentException("Choose a PNG, JPEG, GIF or WebP image.")
    }
    if (bytes.size <= MAX_IMAGE_BYTES) return PickedImage(bytes, ext)

    // Too big to sync: downscale and re-encode until it fits.
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException("Couldn't read the image.")
    while (true) {
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        if (png.size <= MAX_IMAGE_BYTES) return PickedImage(png, "png")
        val jpg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        if (jpg.size <= MAX_IMAGE_BYTES) return PickedImage(jpg, "jpg")
        require(bitmap.width > 200) { "The image is too large to store." }
        bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * 3 / 4, bitmap.height * 3 / 4, true)
    }
}
