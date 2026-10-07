package kz.arctan.grepractice

import kz.arctan.grepractice.data.SampleQuestions
import kz.arctan.grepractice.data.needsFigure
import kz.arctan.grepractice.ui.math.MathSegment
import kz.arctan.grepractice.ui.math.isInsideMath
import kz.arctan.grepractice.ui.math.mathToPlain
import kz.arctan.grepractice.ui.math.normalizeMath
import kz.arctan.grepractice.ui.math.parseMath
import kz.arctan.grepractice.ui.math.rewritePiecewise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathMarkupTest {
    @Test
    fun splitsInlineAndDisplayMath() {
        assertEquals(
            listOf(
                MathSegment.Text("If "),
                MathSegment.Inline("f(x) = x^2"),
                MathSegment.Text(" then"),
                MathSegment.Display("\\int_0^1 f"),
                MathSegment.Text(" is"),
            ),
            parseMath($$"""If $f(x) = x^2$ then$$\int_0^1 f$$ is"""),
        )
    }

    @Test
    fun supportsBracketDelimiters() {
        assertEquals(
            listOf(MathSegment.Inline("a"), MathSegment.Text(" and "), MathSegment.Display("b")),
            parseMath("""\(a\) and \[b\]"""),
        )
    }

    @Test
    fun escapedDollarIsLiteral() {
        assertEquals(listOf(MathSegment.Text("costs $5 or $6")), parseMath("""costs \$5 or \$6"""))
        // \$ inside math does not close it.
        assertEquals(listOf(MathSegment.Inline("\\$ x")), parseMath($$"""$\$ x$"""))
    }

    @Test
    fun unclosedDelimiterStaysText() {
        assertEquals(listOf(MathSegment.Text("a \$b")), parseMath("a \$b"))
        assertEquals(listOf(MathSegment.Text("plain")), parseMath("plain"))
    }

    @Test
    fun detectsCursorInsideMath() {
        val s = "ab \$x^2\$ cd \$\$y\$\$"
        assertFalse(isInsideMath(s, 2))
        assertTrue(isInsideMath(s, 5))
        assertFalse(isInsideMath(s, 10))
        assertTrue(isInsideMath(s, 14))
    }

    @Test
    fun usesMinusCommandExceptInText() {
        assertEquals(
            """1\minus{}x^{\minus1}+\text{well-defined set}\minus\operatorname{arc-sin}x""",
            normalizeMath("""1 - x^{-1} + \text{well-defined set} - \operatorname{arc-sin} x"""),
        )
        assertEquals("""\textstyle\minus1""", normalizeMath("""\textstyle -1"""))
    }

    @Test
    fun rewritesPiecewiseIntoAligned() {
        // cases → aligned with left-aligned columns
        assertEquals(
            """f(x)=\left\{\begin{aligned}&x/2, &&x\text{ rational},\\ &x/3, &&x\text{ irrational}.\end{aligned}\right.""",
            rewritePiecewise("""f(x)=\begin{cases}x/2,&x\text{ rational},\\ x/3,&x\text{ irrational}.\end{cases}"""),
        )
        // brace around an array
        assertEquals(
            """f(x)=\left\{\begin{aligned}&3x^2 &&\text{if } x\in\mathbb{Q}\\ &-5x^2 &&\text{if } x\notin\mathbb{Q}\end{aligned}\right.""",
            rewritePiecewise("""f(x)=\left\{\begin{array}{ll} 3x^2 & \text{if } x\in\mathbb{Q}\\ -5x^2 & \text{if } x\notin\mathbb{Q} \end{array}\right."""),
        )
        // Nested environments and braces inside a cell aren't split.
        assertEquals(
            """\left\{\begin{aligned}&\begin{pmatrix}1&2\\3&4\end{pmatrix} &&\text{if } \{a\}\\ &0 &&\text{else}\end{aligned}\right.""",
            rewritePiecewise("""\begin{cases}\begin{pmatrix}1&2\\3&4\end{pmatrix}&\text{if } \{a\}\\0&\text{else}\end{cases}"""),
        )
        // Arrays that aren't one-sided braces, and other text, are left alone.
        val table = """\left(\begin{array}{cc}1&2\end{array}\right)"""
        assertEquals(table, rewritePiecewise(table))
        assertEquals("x+1", rewritePiecewise("x+1"))
    }

    @Test
    fun detachesScriptsFromStyledGroups() {
        assertEquals("""x\in\mathbb{R}{}^2""", normalizeMath("""x \in \mathbb{R}^2"""))
        assertEquals("""\mathbb{Z}{}_{10}+\mathbf{v}{}_1""", normalizeMath("""\mathbb{Z}_{10}+\mathbf{v}_1"""))
        assertEquals("""5\text{ cm}{}^2""", normalizeMath("""5\text{ cm}^2"""))
        assertEquals("""\mathbb{R}\to{}x^2""", normalizeMath("""\mathbb{R}\to x^2"""))
    }

    @Test
    fun dropsMathSpacesLikeTex() {
        assertEquals("""2\pi{}ie""", normalizeMath("""2\pi i e"""))
        assertEquals("""\int_0^1x\,dx""", normalizeMath("""\int_0^1 x \, dx"""))
        assertEquals("""\cos\theta""", normalizeMath("""\cos \theta"""))
        assertEquals("""a\ b""", normalizeMath("""a\ b"""))
    }

    @Test
    fun parsesImagesOutsideMath() {
        assertEquals(
            listOf(
                MathSegment.Text("The graph of "),
                MathSegment.Inline("f'"),
                MathSegment.Text(" is shown."),
                MathSegment.Image("abc123.png", "figure"),
                MathSegment.Text(" Which is largest?"),
            ),
            parseMath($$"""The graph of $f'$ is shown.![figure](img:abc123.png) Which is largest?"""),
        )
        // Not an img: reference → plain text.
        assertEquals(listOf(MathSegment.Text("![x](http://a.b/c.png)")), parseMath("![x](http://a.b/c.png)"))
        assertEquals("see [figure]", mathToPlain("see ![](img:abc.png)"))
    }

    @Test
    fun detectsQuestionsThatNeedAFigure() {
        assertTrue(needsFigure($$"""The graph of $f'$ is shown. Which ordering is correct?""", listOf("a", "b")))
        assertTrue(needsFigure("In the figure above, as r increases …", listOf("a")))
        assertTrue(needsFigure("Which of the following could be the graph of a solution?", listOf("a")))
        assertFalse(needsFigure("The line tangent to the graph of y = x at 0 is", listOf("a")))
        assertFalse(needsFigure("In the figure above …\n![figure](img:0123456789abcdef.png)", listOf("a")))
        // Graph answers as images in the choices also count.
        assertFalse(needsFigure("Which of the following could be the graph of f?", listOf("![graph](img:0123456789abcdef.png)")))
    }

    @Test
    fun stripsDelimitersForPlainText() {
        assertEquals("area x^2 here", mathToPlain($$"""area $x^2$ here"""))
    }

    @Test
    fun sampleQuestionsHaveBalancedMath() {
        SampleQuestions.all.forEach { q ->
            (listOf(q.text, q.explanation) + q.choices).forEach { field ->
                val strayDollar = parseMath(field).any { it is MathSegment.Text && '$' in it.text }
                assertFalse(strayDollar, "unbalanced \$ in ${q.id}: $field")
            }
        }
    }
}
