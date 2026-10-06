package kz.arctan.grepractice.ui.math

/**
 * Text with embedded LaTeX, using the usual Markdown/MathJax delimiters:
 * `$…$` or `\(…\)` for inline math, `$$…$$` or `\[…\]` for display math, and `\$` for a literal dollar sign.
 * An unclosed delimiter is kept as plain text. Images use Markdown syntax with an `img:` reference,
 * `![alt](img:<name>)`, and are shown on their own line.
 */
sealed interface MathSegment {
    data class Text(val text: String) : MathSegment
    data class Inline(val latex: String) : MathSegment
    data class Display(val latex: String) : MathSegment
    data class Image(val name: String, val alt: String) : MathSegment
}

/** Matches `![alt](img:name)` at the start of the remaining input. */
private val ImageMarkup = Regex("""!\[([^\]]*)]\(img:([^)\s]+)\)""")

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
            input.startsWith("![", i) && ImageMarkup.matchAt(input, i) != null -> {
                val match = ImageMarkup.matchAt(input, i)!!
                flushText()
                out += MathSegment.Image(name = match.groupValues[2], alt = match.groupValues[1])
                i = match.range.last + 1
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

private val TextCommands = listOf("\\text", "\\textrm", "\\textit", "\\textbf", "\\mathrm", "\\operatorname", "\\mbox")

/**
 * Adapts LaTeX source to how TeX treats math mode, where the renderer differs:
 * - ASCII hyphens become `\minus`, drawn as a real minus sign with binary-operator spacing.
 * - Spaces are dropped (TeX ignores them in math and spaces atoms itself; the renderer would draw
 *   them). A space that ends a command name before a letter, as in `\pi i`, becomes `{}`.
 *
 * Arguments of text commands such as `\text{well-defined set}` are left untouched.
 */
fun normalizeMath(latex: String): String {
    if ('-' !in latex && ' ' !in latex) return latex
    val out = StringBuilder(latex.length)
    var afterLetterCommand = false
    var i = 0
    while (i < latex.length) {
        val c = latex[i]
        if (c == ' ' || c == '\t' || c == '\n') {
            while (i < latex.length && latex[i].isWhitespace()) i++
            if (afterLetterCommand && latex.getOrNull(i)?.isLetter() == true) out.append("{}")
            afterLetterCommand = false
            continue
        }
        val command = TextCommands.firstOrNull { latex.startsWith(it, i) && latex.getOrNull(i + it.length)?.isLetter() != true }
        if (command != null) {
            // Copy the command and its brace group verbatim.
            var j = i + command.length
            while (j < latex.length && latex[j] == ' ') j++
            if (j < latex.length && latex[j] == '{') {
                var depth = 0
                while (j < latex.length) {
                    if (latex[j] == '{') depth++ else if (latex[j] == '}' && --depth == 0) { j++; break }
                    j++
                }
            }
            out.append(latex, i, j)
            i = j
            afterLetterCommand = false
            continue
        }
        if (c == '\\' && latex.getOrNull(i + 1)?.isLetter() == true) {
            var j = i + 1
            while (j < latex.length && latex[j].isLetter()) j++
            out.append(latex, i, j)
            i = j
            afterLetterCommand = true
            continue
        }
        if (c == '\\' && i + 1 < latex.length) {
            // Control symbols like \, \{ \\ are copied as a pair (so "\ " keeps its space).
            out.append(latex, i, i + 2)
            i += 2
            afterLetterCommand = false
            continue
        }
        if (c == '-') {
            // \minus is classified as a binary operator (made unary after "(", "=", etc. as in TeX);
            // the bare "-" or "−" characters are not, so they lose the operator spacing.
            out.append("\\minus")
            if (latex.getOrNull(i + 1)?.isLetter() == true) out.append("{}")
        } else {
            out.append(c)
        }
        afterLetterCommand = c == '-'
        i++
    }
    return out.toString()
}

/** Strips math delimiters for places where rendering isn't possible (e.g. dialog text, titles). */
fun mathToPlain(input: String): String = parseMath(input).joinToString("") {
    when (it) {
        is MathSegment.Text -> it.text
        is MathSegment.Inline -> it.latex
        is MathSegment.Display -> it.latex
        is MathSegment.Image -> "[${it.alt.ifBlank { "figure" }}]"
    }
}
