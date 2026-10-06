package kz.arctan.grepractice

import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.PracticeSession
import kz.arctan.grepractice.practice.findPracticeTests
import kz.arctan.grepractice.practice.isPracticeTestQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeTestsTest {
    private fun q(id: String) = Question(id = id, topic = "Calculus", text = id, choices = listOf("a", "b"), correctIndex = 0)

    @Test
    fun groupsQuestionsIntoTestsInNumberOrder() {
        val bank = listOf(q("practice3-02"), q("sample-01"), q("practice2-10"), q("practice3-01"), q("practice2-9"), q("math1-05"))
        val tests = findPracticeTests(bank)
        assertEquals(listOf(2, 3), tests.map { it.number })
        assertEquals(listOf("practice2-9", "practice2-10"), tests[0].questions.map { it.id })
        assertEquals(listOf("practice3-01", "practice3-02"), tests[1].questions.map { it.id })
        assertEquals("practice3", tests[1].id)
    }

    @Test
    fun recognisesOnlyPracticeIds() {
        assertTrue(isPracticeTestQuestion("practice5-66"))
        assertFalse(isPracticeTestQuestion("sample-01"))
        assertFalse(isPracticeTestQuestion("practice5"))
        assertFalse(isPracticeTestQuestion("mypractice5-01"))
    }

    @Test
    fun fullTestGetsRealExamTimingAndRecordsItsId() {
        val test = findPracticeTests((1..66).map { q("practice4-" + it.toString().padStart(2, '0')) }).single()
        assertEquals(170 * 60_000L, test.timeLimitMs)

        val timed = test.config(timed = true)
        assertEquals(170 * 60_000L, timed.timeLimitMs)
        assertFalse(timed.instantFeedback)
        assertFalse(timed.shuffleChoices)
        assertEquals((1..66).map { "practice4-" + it.toString().padStart(2, '0') }, timed.questions.map { it.id })

        val untimed = test.config(timed = false)
        assertNull(untimed.timeLimitMs)
        assertTrue(untimed.instantFeedback)

        assertEquals("practice4", PracticeSession(timed).finish().testId)
    }
}
