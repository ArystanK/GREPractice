package kz.arctan.grepractice.ui

import androidx.compose.runtime.Composable

/** Image bytes plus their file extension ("png", "jpg", …), already shrunk to fit the sync size limit. */
class PickedImage(val bytes: ByteArray, val extension: String)

/**
 * Returns a function that opens the platform's image picker. [onResult] gets the image, null if the
 * user cancelled, or a failure (e.g. an unreadable file).
 */
@Composable
expect fun rememberImagePicker(onResult: (Result<PickedImage?>) -> Unit): () -> Unit

/** Whether [pasteImageFromClipboard] is available (desktop: paste a screenshot). */
expect val canPasteImage: Boolean

/** The image on the clipboard, or null if there is none. */
expect fun pasteImageFromClipboard(): PickedImage?
