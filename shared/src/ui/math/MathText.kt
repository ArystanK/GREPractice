package kz.arctan.grepractice.ui.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

/** KaTeX's math font looks small next to UI text at the same size, as in browsers. */
private const val MATH_SCALE = 1.1f

/**
 * Where Compose's `TextCenter` placeholder alignment puts the line center, as a fraction of the
 * font size above the baseline. Inline formulas are padded around this point so their baseline
 * lines up with the surrounding text.
 */
private const val TEXT_CENTER_ABOVE_BASELINE = 0.33f

/**
 * Renders text containing LaTeX math (see [parseMath]). Text without math takes a plain [Text]
 * fast path. Inline formulas flow with the text; display formulas get their own centered line.
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    /** Height limit for images (`![alt](img:name)`); lists pass a small value for compact previews. */
    imageMaxHeight: Dp = 360.dp,
) {
    val segments = remember(text) { prepareSegments(parseMath(text)) }
    if (segments.none { it !is MathSegment.Text }) {
        Text(text = segments.joinToString("") { (it as MathSegment.Text).text }, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }

    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp
    val config = remember(textColor, fontSize) { mathConfig(textColor, fontSize * MATH_SCALE) }
    val displayConfig = remember(textColor, fontSize) { mathConfig(textColor, fontSize * (MATH_SCALE * 1.15f)) }

    // Split into paragraphs of text + inline math, separated by display formulas and images.
    val blocks = remember(segments) {
        buildList<List<MathSegment>> {
            var current = mutableListOf<MathSegment>()
            segments.forEach { seg ->
                if (seg is MathSegment.Display || seg is MathSegment.Image) {
                    if (current.isNotEmpty()) add(current)
                    add(listOf(seg))
                    current = mutableListOf()
                } else {
                    current += seg
                }
            }
            if (current.isNotEmpty()) add(current)
        }
    }

    if (blocks.size == 1 && blocks[0].first() !is MathSegment.Display && blocks[0].first() !is MathSegment.Image) {
        InlineParagraph(blocks[0], config, fontSize, modifier, style, textColor, maxLines)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (val first = block.first()) {
                is MathSegment.Display -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Latex(latex = first.latex, config = displayConfig)
                }
                is MathSegment.Image -> QuestionImage(first.name, first.alt, imageMaxHeight)
                else -> InlineParagraph(block, config, fontSize, Modifier, style, textColor, maxLines)
            }
        }
    }
}


@Composable
private fun InlineParagraph(
    segments: List<MathSegment>,
    config: LatexConfig,
    fontSize: TextUnit,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
    maxLines: Int,
) {
    val measurer = rememberLatexMeasurer(config)
    val density = LocalDensity.current
    val inlineContent = HashMap<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        segments.forEachIndexed { index, seg ->
            when (seg) {
                is MathSegment.Text -> append(seg.text)
                is MathSegment.Inline -> {
                    val dims = measurer.measure(seg.latex, config)
                    if (dims == null) {
                        // Unparseable formula: show the source rather than nothing.
                        append(seg.latex)
                    } else {
                        val id = "math$index"
                        with(density) {
                            val center = fontSize.toPx() * TEXT_CENTER_ABOVE_BASELINE
                            val above = dims.baselinePx - center
                            val below = dims.heightPx - dims.baselinePx + center
                            val half = maxOf(above, below)
                            val topPad = (half - above).toDp()
                            inlineContent[id] = InlineTextContent(
                                Placeholder(dims.widthPx.toSp(), (2 * half).toSp(), PlaceholderVerticalAlign.TextCenter),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    Latex(latex = seg.latex, config = config, modifier = Modifier.offset(y = topPad))
                                }
                            }
                        }
                        appendInlineContent(id, seg.latex)
                    }
                }
                is MathSegment.Display, is MathSegment.Image -> {}
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent,
    )
}

private fun MathSegment?.isBlock() = this is MathSegment.Display || this is MathSegment.Image

/** Fixes minus signs and drops whitespace next to display formulas, which sit on their own line anyway. */
private fun prepareSegments(segments: List<MathSegment>): List<MathSegment> = segments.mapIndexedNotNull { i, seg ->
    when (seg) {
        is MathSegment.Inline -> MathSegment.Inline(normalizeMath(seg.latex))
        is MathSegment.Display -> MathSegment.Display(normalizeMath(seg.latex))
        is MathSegment.Image -> seg
        is MathSegment.Text -> {
            var t = seg.text
            if (segments.getOrNull(i - 1).isBlock()) t = t.trimStart()
            if (segments.getOrNull(i + 1).isBlock()) t = t.trimEnd()
            if (t.isEmpty()) null else MathSegment.Text(t)
        }
    }
}

private fun mathConfig(color: Color, fontSize: TextUnit) = LatexConfig(
    fontSize = fontSize,
    theme = LatexTheme.light(color = color, backgroundColor = Color.Transparent),
)

/** Renders a bare LaTeX formula (no delimiters), e.g. for palette buttons. */
@Composable
fun LatexFormula(latex: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val config = remember(color, fontSize) { mathConfig(color, fontSize) }
    Latex(latex = normalizeMath(latex), config = config, modifier = modifier)
}
