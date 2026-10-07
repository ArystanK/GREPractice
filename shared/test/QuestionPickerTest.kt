package kz.arctan.grepractice

import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.PickReason
import kz.arctan.grepractice.practice.QuestionPicker
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestionPickerTest {
    private fun q(id: String, topic: String = "Calculus") = Question(id = id, topic = topic, text = id, choices = listOf("a", "b"), correctIndex = 0)

    private fun answer(q: Question, selected: Int?, timeMs: Long = 60_000) =
        AnswerRecord(q.id, q.topic, q.text, q.choices, q.correctIndex, selectedIndex = selected, timeSpentMs = timeMs)

    private fun result(vararg answers: AnswerRecord) =
        PracticeResult(id = answers.first().questionId, mode = PracticeMode.RANDOM, title = "", startedAt = 0, durationMs = 0, answers = answers.toList())

    private fun counts(picker: QuestionPicker, pool: List<Question>, draws: Int = 4000): Map<String, Int> {
        val random = Random(42)
        return (1..draws).map { picker.pick(pool, random)!!.question.id }.groupingBy { it }.eachCount()
    }

    @Test
    fun judgesQuestionsByTheirLatestAttempt() {
        val (a, b, c, d) = listOf(q("a"), q("b"), q("c"), q("d"))
        // Newest first: "a" was missed, then answered correctly; "b" the other way round.
        val picker = QuestionPicker(listOf(result(answer(a, 0), answer(b, 1), answer(c, null)), result(answer(a, 1), answer(b, 0))))
        assertEquals(PickReason.REVIEW, picker.reasonFor(a))
        assertEquals(PickReason.MISSED, picker.reasonFor(b))
        assertEquals(PickReason.SKIPPED, picker.reasonFor(c))
        assertEquals(PickReason.UNSEEN, picker.reasonFor(d))
    }

    @Test
    fun aFewMissedQuestionsStillComeUpOftenInALargeUnseenBank() {
        val missed = q("missed")
        val correct = q("correct")
        val unseen = (1..500).map { q("u$it") }
        val picker = QuestionPicker(listOf(result(answer(missed, 1), answer(correct, 0))))
        val n = counts(picker, listOf(missed, correct) + unseen)
        // Shares: missed 45%, unanswered 40%, review 15%.
        assertTrue(n.getValue("missed") in 1650..1950, "missed ${n["missed"]}")
        assertTrue(n.getValue("correct") in 450..750, "correct ${n["correct"]}")
        assertTrue(unseen.sumOf { n[it.id] ?: 0 } in 1450..1750)
    }

    @Test
    fun slowTopicsAreWeightedUpWithinABucket() {
        val slow = (1..3).map { q("s$it", "Topology") }
        val fast = (1..3).map { q("f$it", "Calculus") }
        val pace = Gre.PACE_MS_PER_QUESTION
        // Answered correctly at twice GRE pace in Topology, within pace in Calculus.
        val history = result(*(slow.map { answer(it, 0, 2 * pace) } + fast.map { answer(it, 0, pace / 2) }).toTypedArray())
        val picker = QuestionPicker(listOf(history))
        assertEquals(2.0, picker.topicWeight("Topology"))
        assertEquals(1.0, picker.topicWeight("Calculus"))
        assertEquals(2 * pace, picker.slowTopicAvgMs("Topology"))
        assertNull(picker.slowTopicAvgMs("Calculus"))

        val newSlow = q("new-slow", "Topology")
        val newFast = q("new-fast", "Calculus")
        val n = counts(picker, listOf(newSlow, newFast))
        assertTrue(n.getValue("new-slow") in 2500..2850, "slow ${n["new-slow"]}") // 2/3 of draws
        assertEquals(newSlow.topic, picker.pick(listOf(newSlow))!!.question.topic)
    }

    @Test
    fun topicTimeNeedsEnoughAnswersAndIsCapped() {
        val pace = Gre.PACE_MS_PER_QUESTION
        val two = (1..2).map { q("t$it", "Topology") }
        assertEquals(1.0, QuestionPicker(listOf(result(*two.map { answer(it, 0, 5 * pace) }.toTypedArray()))).topicWeight("Topology"))
        val many = (1..5).map { q("t$it", "Topology") }
        assertEquals(QuestionPicker.MAX_TOPIC_WEIGHT, QuestionPicker(listOf(result(*many.map { answer(it, 0, 10 * pace) }.toTypedArray()))).topicWeight("Topology"))
        // Unanswered records don't count towards the topic's time.
        val blanks = (1..5).map { q("b$it", "Geometry") }
        assertEquals(1.0, QuestionPicker(listOf(result(*blanks.map { answer(it, null, 10 * pace) }.toTypedArray()))).topicWeight("Geometry"))
    }

    @Test
    fun emptyPoolPicksNothing() {
        assertNull(QuestionPicker(emptyList()).pick(emptyList()))
    }
}
