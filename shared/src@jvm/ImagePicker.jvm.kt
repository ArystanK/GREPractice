package kz.arctan.grepractice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kz.arctan.grepractice.data.MAX_IMAGE_BYTES
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

private val ImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp")

@Composable
actual fun rememberImagePicker(onResult: (Result<PickedImage?>) -> Unit): () -> Unit = remember(onResult) {
    {
        val dialog = FileDialog(null as Frame?, "Choose an image", FileDialog.LOAD).apply {
            file = "*.png;*.jpg;*.jpeg;*.gif;*.webp"
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in ImageExtensions }
            isVisible = true
        }
        val name = dialog.file
        onResult(
            if (name == null) {
                Result.success(null)
            } else {
                runCatching { fromFile(File(dialog.directory, name)) }
            },
        )
    }
}

actual val canPasteImage: Boolean = true

actual fun pasteImageFromClipboard(): PickedImage? {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
        val image = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return null
        return encode(toBufferedImage(image))
    }
    // A copied image file (e.g. from Explorer).
    if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
        val file = (clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>)?.firstOrNull() as? File ?: return null
        if (file.extension.lowercase() in ImageExtensions) return fromFile(file)
    }
    return null
}

private fun fromFile(file: File): PickedImage {
    val ext = file.extension.lowercase().let { if (it == "jpeg") "jpg" else it }
    require(ext in ImageExtensions) { "Choose a PNG, JPEG, GIF or WebP image." }
    val bytes = file.readBytes()
    if (bytes.size <= MAX_IMAGE_BYTES) return PickedImage(bytes, ext)
    val image = ImageIO.read(file) ?: throw IllegalArgumentException("Couldn't read ${file.name} as an image.")
    return encode(image)
}

/** PNG if it fits the size limit (crisp line art); otherwise downscaled, then JPEG as a last resort. */
private fun encode(source: BufferedImage): PickedImage {
    var image = source
    while (true) {
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        if (png.size <= MAX_IMAGE_BYTES) return PickedImage(png, "png")
        val jpg = jpeg(image)
        if (jpg.size <= MAX_IMAGE_BYTES) return PickedImage(jpg, "jpg")
        require(image.width > 200) { "The image is too large to store." }
        image = scale(image, 0.75)
    }
}

private fun jpeg(image: BufferedImage): ByteArray {
    val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().apply {
            color = java.awt.Color.WHITE
            fillRect(0, 0, width, height)
            drawImage(image, 0, 0, null)
            dispose()
        }
    }
    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.85f
        }
        writer.write(null, IIOImage(rgb, null, null), params)
        writer.dispose()
    }
    return out.toByteArray()
}

private fun scale(image: BufferedImage, factor: Double): BufferedImage {
    val w = (image.width * factor).toInt().coerceAtLeast(1)
    val h = (image.height * factor).toInt().coerceAtLeast(1)
    return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(image, 0, 0, w, h, null)
            dispose()
        }
    }
}

private fun toBufferedImage(image: Image): BufferedImage {
    if (image is BufferedImage) return image
    val w = image.getWidth(null)
    val h = image.getHeight(null)
    require(w > 0 && h > 0) { "The clipboard image is empty." }
    return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().apply {
            drawImage(image, 0, 0, null)
            dispose()
        }
    }
}
