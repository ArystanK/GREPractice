package kz.arctan.grepractice.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.ui.math.MathSegment
import kz.arctan.grepractice.ui.math.parseMath

private const val IMAGES_DIR = "images"

/**
 * Raw image size limit. Images sync as base64 inside one Firestore document, which must stay under
 * the 900 KB payload limit in firestore.rules.
 */
const val MAX_IMAGE_BYTES = 600_000

private val ImageName = Regex("""[0-9a-f]{16,64}\.(png|jpg|jpeg|gif|webp)""")

/**
 * Question figures, stored as `images/<hash>.<ext>` in the data directory and referenced from
 * question text as `![alt](img:<hash>.<ext>)`. Names are content hashes, so an image never changes.
 */
object ImageStore {
    /** Bumped whenever an image is added locally, so composables showing a missing image retry. */
    var version by mutableIntStateOf(0)
        private set

    fun isValidName(name: String) = ImageName.matches(name)

    fun load(name: String): ByteArray? = if (isValidName(name)) readDataBytes("$IMAGES_DIR/$name") else null

    fun exists(name: String): Boolean = load(name) != null

    /** Stores [bytes] and returns the name to reference it by. */
    fun add(bytes: ByteArray, extension: String): String {
        require(bytes.size <= MAX_IMAGE_BYTES) {
            "The image is ${bytes.size / 1000} KB; the limit is ${MAX_IMAGE_BYTES / 1000} KB. Crop it or save it smaller."
        }
        val ext = extension.lowercase().removePrefix(".").let { if (it == "jpeg") "jpg" else it }
        require(ext in setOf("png", "jpg", "gif", "webp")) { "Unsupported image type: $extension" }
        val name = sha256Hex(bytes).take(32) + "." + ext
        if (!exists(name)) {
            writeDataBytes("$IMAGES_DIR/$name", bytes)
            version++
        }
        return name
    }

    /** Stores an image received from the cloud under its existing [name]. */
    internal fun put(name: String, bytes: ByteArray) {
        if (!isValidName(name)) return
        writeDataBytes("$IMAGES_DIR/$name", bytes)
        version++
    }
}

/** Markup that shows image [name] in question text, choices or explanations. */
fun imageMarkup(name: String, alt: String = "figure") = "![$alt](img:$name)"

/** Names of all images referenced in [text]. */
fun imageRefs(text: String): List<String> =
    parseMath(text).filterIsInstance<MathSegment.Image>().map { it.name }

fun Question.imageRefs(): List<String> = (listOf(text, explanation) + choices).flatMap(::imageRefs)

/**
 * Wording that means the question depends on a picture: "the figure above", "the graph shown",
 * "(figure omitted)", "which of the following could be the graph of …" (graphs as answer choices).
 */
private val FigureWording = Regex(
    listOf(
        """\bfigures?\b""",
        """\b(shown|pictured|indicated|illustrated|drawn)\s+(above|below|here)\b""",
        """\bas shown\b""",
        """\b(graph|curve|path|region|diagram)\s+shown\b""",
        """\b(is|are) shown\b""",
        """\babove is the graph\b""",
        """\bshaded\b""",
        """\b(could|might|best) (be|represents?) (the|a portion of the) graph\b""",
        """\b(represents?|indicates?) (the|a portion of the) graph\b""",
        """\bpart of the graph\b""",
        """\bwhich of the following (is|could be|best represents|indicates) the graph\b""",
        """\bomitted\b""",
    ).joinToString("|"),
    RegexOption.IGNORE_CASE,
)

/** Choices that stand in for a picture, like "Graph A". */
private val PlaceholderChoice = Regex("""\s*(graph|figure|diagram|picture|matrix)\s*\(?[A-E]\)?\s*""", RegexOption.IGNORE_CASE)

/**
 * True if the question refers to a figure (or to content marked "omitted") but has no image, or if
 * its choices are placeholders such as "Graph A" instead of the actual graphs.
 */
fun needsFigure(text: String, choices: List<String>): Boolean {
    if (choices.any { PlaceholderChoice.matches(it) }) return true
    return FigureWording.containsMatchIn(text) && (listOf(text) + choices).none { imageRefs(it).isNotEmpty() }
}

fun Question.needsFigure(): Boolean = needsFigure(text, choices)
