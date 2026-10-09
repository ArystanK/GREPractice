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

private val TextCommands = listOf("\\text", "\\textrm", "\\textit", "\\textbf", "\\texttt", "\\textsf", "\\mathrm", "\\operatorname", "\\mbox")

/**
 * Adapts LaTeX source to how TeX treats math mode, where the renderer differs:
 * - ASCII hyphens become `\minus`, drawn as a real minus sign with binary-operator spacing.
 * - Spaces are dropped (TeX ignores them in math and spaces atoms itself; the renderer would draw
 *   them). A space that ends a command name before a letter, as in `\pi i`, becomes `{}`.
 *
 * Arguments of text commands such as `\text{well-defined set}` are left untouched.
 */
fun normalizeMath(source: String): String =
    detachStyledScripts(dropSpacesAndMinus(groupAfterOpenBrace(rewriteOperatorScripts(rewritePiecewise(source)))))

/** A sign or operator name right after an escaped opening brace, as in `\{-3i\}` or `\{\pm1\}`. */
private val AfterOpenBrace = Regex("""\\\{\s*(-|\\pm|\\mp|\\(?:arcsin|arccos|arctan|sinh|cosh|tanh|sin|cos|tan|cot|sec|csc|log|ln|exp|arg|det|dim|ker|gcd)(?![a-zA-Z]))""")

/**
 * The renderer doesn't treat `\{` as an opening delimiter, so a minus sign after it gets binary-operator
 * spacing ("{ − 3i}") and an operator name a leading space. A group around it, `\{{-}3i\}`, renders
 * as TeX does.
 */
internal fun groupAfterOpenBrace(latex: String): String = AfterOpenBrace.replace(latex) { "\\{{${it.groupValues[1]}}" }

/** Function names whose scripts follow the name, as in $\sin^2 x$ or $\log_2 x$ (not $\lim$-style limits). */
private val OperatorScript = Regex("""\\(arcsin|arccos|arctan|sinh|cosh|tanh|coth|sin|cos|tan|cot|sec|csc|log|ln|lg|exp)(?![a-zA-Z])\s*(?=[\^_])""")

/**
 * The renderer places scripts on operator names such as `\cos` far from the name, as if the
 * operator's spacing came first. Writing the name upright instead, `\mathrm{cos}^{23}`, keeps the
 * script on it (with [detachStyledScripts] fixing its height); the operator's thin space before the
 * argument is restored with `\,`, except before an opening bracket, where TeX adds none either.
 */
internal fun rewriteOperatorScripts(latex: String): String {
    if ('\\' !in latex) return latex
    val out = StringBuilder()
    var i = 0
    while (true) {
        val m = OperatorScript.find(latex, i) ?: break
        out.append(latex, i, m.range.first).append("\\mathrm{").append(m.groupValues[1]).append('}')
        var j = m.range.last + 1
        // Copy the scripts: one or two of ^ / _, each with a brace group, a command or a single character.
        while (j < latex.length && (latex[j] == '^' || latex[j] == '_')) {
            val start = j++
            while (j < latex.length && latex[j] == ' ') j++
            j = when {
                j >= latex.length -> j
                latex[j] == '{' -> {
                    var depth = 0
                    var k = j
                    while (k < latex.length) {
                        if (latex[k] == '{') depth++ else if (latex[k] == '}' && --depth == 0) break
                        k++
                    }
                    k + 1
                }
                latex[j] == '\\' -> {
                    var k = j + 1
                    while (k < latex.length && latex[k].isLetter()) k++
                    if (k == j + 1) k + 1 else k
                }
                else -> j + 1
            }.coerceAtMost(latex.length)
            out.append(latex, start, j)
        }
        var k = j
        while (k < latex.length && latex[k] == ' ') k++
        val next = latex.substring(k)
        val opening = next.isEmpty() || next[0] in "([|" || next.startsWith("\\{") || next.startsWith("\\left") ||
            next.startsWith("\\big") || next.startsWith("\\Big") || next.startsWith("\\,")
        if (!opening) out.append("\\,")
        i = if (opening) j else k
    }
    out.append(latex, i, latex.length)
    return out.toString()
}

/** A font or text group directly followed by a superscript or subscript, such as `\mathbb{R}^`. */
private val StyledScript = Regex("""(\\(?:mathbb|mathbf|mathcal|mathfrak|mathsf|mathit|mathrm|boldsymbol|operatorname|text[a-z]*|mbox)\{[^{}]*\})(?=[\^_])""")

/**
 * The renderer attaches scripts to a styled group as if it were very tall, so `\mathbb{R}^2` gets
 * its exponent far above the letter. An empty group in between, `\mathbb{R}{}^2`, places them as TeX does.
 */
private fun detachStyledScripts(latex: String): String = StyledScript.replace(latex, "$1{}")

private fun dropSpacesAndMinus(latex: String): String {
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

/** `\begin{cases}`/`\begin{dcases}`, or a brace around an array: `\left\{ \begin{array}{…}`. */
private val PiecewiseStart = Regex("""\\begin\{(d?cases)\}|\\left\\\{\s*\\begin\{array\}\{[^}]*\}""")
private val RightDot = Regex("""^\s*\\right\.""")

/**
 * Rewrites piecewise definitions into `\left\{\begin{aligned} &a &&b \\ … \end{aligned}\right.`, the
 * one form the renderer lays out correctly: its `cases` swaps the columns and adds a stray "if", and
 * a brace around an `array` is sized and placed wrongly. The `&…&&` pattern left-aligns each column,
 * as TeX's `cases` does.
 */
internal fun rewritePiecewise(latex: String): String {
    var s = latex
    var searchFrom = 0
    while (true) {
        val m = PiecewiseStart.find(s, searchFrom) ?: return s
        val isArray = m.value.startsWith("\\left")
        val env = if (isArray) "array" else m.groupValues[1]
        val bodyStart = m.range.last + 1
        val end = matchingEnd(s, bodyStart, env)
        if (end < 0) return s
        var after = end + "\\end{$env}".length
        if (isArray) {
            val right = RightDot.find(s.substring(after))
            if (right == null) {
                searchFrom = after // a brace that isn't one-sided: leave it alone
                continue
            }
            after += right.value.length
        }
        val rows = splitTopLevel(s.substring(bodyStart, end), "\\\\")
            .map { row -> splitTopLevel(row, "&").map { it.trim() } }
            .filter { cells -> cells.any { it.isNotEmpty() } }
        val body = rows.joinToString("\\\\ ") { cells -> "&" + cells.joinToString(" &&") }
        val replacement = "\\left\\{\\begin{aligned}$body\\end{aligned}\\right."
        s = s.substring(0, m.range.first) + replacement + s.substring(after)
        searchFrom = m.range.first + replacement.length
    }
}

/** Index of the `\end{env}` matching an environment whose body starts at [from], or -1. */
private fun matchingEnd(s: String, from: Int, env: String): Int {
    var depth = 0
    var i = from
    while (i < s.length) {
        when {
            s.startsWith("\\begin{$env}", i) -> depth++
            s.startsWith("\\end{$env}", i) -> if (depth == 0) return i else depth--
        }
        i++
    }
    return -1
}

/** Splits [body] at [separator] occurrences outside braces and nested environments. */
private fun splitTopLevel(body: String, separator: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var start = 0
    var i = 0
    while (i < body.length) {
        if (depth == 0 && body.startsWith(separator, i) && !(separator == "&" && i > 0 && body[i - 1] == '\\')) {
            parts += body.substring(start, i)
            i += separator.length
            start = i
            continue
        }
        when {
            // Skip the whole \begin{name} / \end{name}, so the name's braces don't count.
            body.startsWith("\\begin{", i) -> { depth++; i = body.indexOf('}', i).let { if (it < 0) body.length else it + 1 } }
            body.startsWith("\\end{", i) -> { depth--; i = body.indexOf('}', i).let { if (it < 0) body.length else it + 1 } }
            body[i] == '\\' -> i += 2 // escaped character or command start, e.g. \{ or \\
            body[i] == '{' -> { depth++; i++ }
            body[i] == '}' -> { depth--; i++ }
            else -> i++
        }
    }
    parts += body.substring(start)
    return parts
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
