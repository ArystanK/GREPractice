package kz.arctan.grepractice.ui.math

/**
 * Text with embedded LaTeX, using the usual Markdown/MathJax delimiters:
 * `$…$` or `\(…\)` for inline math, `$$…$$` or `\[…\]` for display math, and `\$` for a literal dollar sign.
 * An unclosed delimiter is kept as plain text.
 */
sealed interface MathSegment {
    data class Text(val text: String) : MathSegment
    data class Inline(val latex: String) : MathSegment
    data class Display(val latex: String) : MathSegment
}

fun parseMath(input: String): List<MathSegment> {
    val out = mutableListOf<MathSegment>()
    val text = StringBuilder()
    fun flushText() {
        if (text.isNotEmpty()) {
            out += MathSegment.Text(text.toString())
            text.clear()
        }
    }

    var i = 0
    while (i < input.length) {
        val (open, close, display) = when {
            input.startsWith("\\$", i) -> {
                text.append('$')
                i += 2
                continue
            }
            input.startsWith("$$", i) -> Triple("$$", "$$", true)
            input[i] == '$' -> Triple("$", "$", false)
            input.startsWith("\\[", i) -> Triple("\\[", "\\]", true)
            input.startsWith("\\(", i) -> Triple("\\(", "\\)", false)
            else -> {
                text.append(input[i])
                i++
                continue
            }
        }
        val end = findClose(input, close, i + open.length)
        if (end < 0) {
            text.append(input, i, input.length)
            break
        }
        flushText()
        val latex = input.substring(i + open.length, end).trim()
        if (latex.isNotEmpty()) out += if (display) MathSegment.Display(latex) else MathSegment.Inline(latex)
        i = end + close.length
    }
    flushText()
    return out
}

/** Index of [close] at or after [from], skipping backslash-escaped characters such as `\$` or `\\`. */
private fun findClose(input: String, close: String, from: Int): Int {
    var j = from
    while (j < input.length) {
        if (input.startsWith(close, j)) return j
        j += if (input[j] == '\\' && close != "\\]" && close != "\\)") 2 else 1
    }
    return -1
}

/** True if [offset] lies inside a math region (used by the editor to decide whether to add `$` delimiters). */
fun isInsideMath(input: String, offset: Int): Boolean {
    var inline = false
    var display = false
    var i = 0
    while (i < offset.coerceAtMost(input.length)) {
        when {
            input.startsWith("\\$", i) -> i += 2
            input.startsWith("$$", i) && !inline -> { display = !display; i += 2 }
            input[i] == '$' && !display -> { inline = !inline; i++ }
            else -> i++
        }
    }
    return inline || display
}

/** Strips math delimiters for places where rendering isn't possible (e.g. dialog text, titles). */
fun mathToPlain(input: String): String = parseMath(input).joinToString("") {
    when (it) {
        is MathSegment.Text -> it.text
        is MathSegment.Inline -> it.latex
        is MathSegment.Display -> it.latex
    }
}
