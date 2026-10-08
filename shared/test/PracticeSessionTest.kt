package kz.arctan.grepractice

import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.PracticeSession
import kz.arctan.grepractice.practice.formatDuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PracticeSessionTest {
    private val questions = (0 until 4).map { i ->
        Question(id = "q$i", topic = if (i < 2) "Calculus" else "Topology", text = "Q$i", choices = listOf("a", "b", "c", "d", "e"), correctIndex = i)
    }

    private fun session(shuffle: Boolean = false, limitMs: Long? = null, instant: Boolean = false) = PracticeSession(
        PracticeConfig(PracticeMode.TOPIC, "Test", questions, limitMs, instantFeedback = instant, shuffleChoices = shuffle),
    )

    @Test
    fun scoresSelectedAnswers() {
        val s = session()
        s.select(0) // q0 correct
        s.goTo(1); s.select(3) // q1 wrong
        s.goTo(2); s.select(2) // q2 correct
        // q3 left unanswered
        val r = s.finish()
        assertEquals(2, r.correct)
        assertEquals(4, r.total)
        assertEquals(50, r.percent)
        assertNull(r.answers[3].selectedIndex)
        assertFalse(r.timedOut)
    }

    @Test
    fun selectingSameChoiceTwiceClearsIt() {
        val s = session()
        s.select(1)
        s.select(1)
        assertNull(s.selected[0])
    }

    @Test
    fun shuffledChoicesKeepTheCorrectAnswer() {
        repeat(20) {
            val s = session(shuffle = true)
            s.items.forEach { item ->
                assertEquals(item.question.choices[item.question.correctIndex], item.choices[item.correctIndex])
                assertEquals(item.question.choices.sorted(), item.choices.sorted())
            }
        }
    }

    @Test
    fun checkedQuestionsAreLocked() {
        val s = session(instant = true)
        s.select(2)
        s.check()
        s.select(0)
        assertEquals(2, s.selected[0])
    }

    @Test
    fun zeroTimeLimitAutoSubmits() {
        val s = session(limitMs = 0)
        assertTrue(s.tick())
        val r = s.finishedResult!!
        assertTrue(r.timedOut)
        assertSame(r, s.finish()) // finishing again returns the same result
        assertFalse(s.tick())
    }

    @Test
    fun aRestoredSessionContinuesWhereItLeftOff() {
        val s = session(shuffle = true, limitMs = 60 * 60_000L, instant = true)
        s.select(1); s.check()
        s.goTo(2); s.select(0); s.toggleFlag()
        val saved = s.snapshot()

        val r = PracticeSession.restore(saved)!!
        assertEquals(s.startedAt, r.startedAt)
        assertEquals(2, r.current)
        assertEquals(s.items.map { it.choices }, r.items.map { it.choices }) // same shuffled order
        assertEquals(s.items.map { it.correctIndex }, r.items.map { it.correctIndex })
        assertEquals(listOf(1, null, 0, null), r.selected.toList())
        assertEquals(listOf(false, false, true, false), r.flagged.toList())
        assertEquals(listOf(true, false, false, false), r.checked.toList())
        assertEquals(saved, r.snapshot())
        // Checked answers stay locked after a restore, and the result matches the original's.
        r.goTo(0); r.select(3)
        assertEquals(1, r.selected[0])
        assertEquals(s.finish().answers.map { it.selectedIndex }, r.finish().answers.map { it.selectedIndex })
    }

    @Test
    fun aRestoredTimedSessionKeepsItsClockAndTimesOut() {
        val s = session(limitMs = 1_000)
        val saved = s.snapshot().copy(startedAt = s.startedAt - 5_000) // the app was closed for a while
        val r = PracticeSession.restore(saved)!!
        assertTrue(r.tick())
        assertTrue(r.finishedResult!!.timedOut)
        assertEquals(1_000, r.finishedResult!!.durationMs)
    }

    @Test
    fun inconsistentSavedSessionsAreRejected() {
        val saved = session().snapshot()
        assertNull(PracticeSession.restore(saved.copy(current = 9)))
        assertNull(PracticeSession.restore(saved.copy(items = emptyList())))
        assertNull(PracticeSession.restore(saved.copy(items = saved.items.map { it.copy(selected = 7) })))
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("2:34", formatDuration(154_000))
        assertEquals("2:50:00", formatDuration(170 * 60_000L))
    }

    @Test
    fun reviewShowsTheCurrentVersionOfAQuestion() {
        val old = kz.arctan.grepractice.model.AnswerRecord("q1", "T", "System omitted", listOf("Graph A", "Graph B"), 1, selectedIndex = 0)
        // Choices replaced in place (same count, same correct position): use the new ones.
        val fixed = Question("q1", "T", "Full system", listOf("![graph](img:a.png)", "![graph](img:b.png)"), 1, explanation = "new")
        val r = old.refreshedFrom(fixed)
        assertEquals("Full system", r.questionText)
        assertEquals(fixed.choices, r.choices)
        assertEquals("new", r.explanation)
        assertEquals(0, r.selectedIndex)
        // Same choices in a shuffled order: keep the recorded order so the indices stay right.
        val shuffled = old.copy(choices = listOf("b", "a"), correctIndex = 0)
        assertEquals(listOf("b", "a"), shuffled.refreshedFrom(Question("q1", "T", "t", listOf("a", "b"), 1)).choices)
        // Deleted question: the snapshot is kept as is.
        assertEquals(old, old.refreshedFrom(null))
    }
}
