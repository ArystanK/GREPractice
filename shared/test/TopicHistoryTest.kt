package kz.arctan.grepractice

import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.practice.topicAttempts
import kz.arctan.grepractice.practice.topicMissedIds
import kz.arctan.grepractice.practice.topicSessionScores
import kz.arctan.grepractice.practice.topicSummaries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopicHistoryTest {
    private fun a(id: String, topic: String, selected: Int?, timeMs: Long = 60_000) =
        AnswerRecord(id, topic, id, listOf("a", "b"), correctIndex = 0, selectedIndex = selected, timeSpentMs = timeMs)

    private fun r(id: String, at: Long, vararg answers: AnswerRecord) =
        PracticeResult(id = id, mode = PracticeMode.TOPIC, title = "S$id", startedAt = at, durationMs = 0, answers = answers.toList())

    // Newest first, as the repository keeps them.
    private val results = listOf(
        r("3", 300, a("q1", "Calculus", 0, 30_000), a("t1", "Topology", 1)),
        r("2", 200, a("q1", "Calculus", 1), a("q2", "Calculus", null, 90_000)),
        r("1", 100, a("q2", "Calculus", 0, 120_000), a("q3", "Calculus", 1)),
    )

    @Test
    fun attemptsAreNewestFirstWithTheirSession() {
        val attempts = topicAttempts(results, "Calculus")
        assertEquals(listOf("3", "2", "2", "1", "1"), attempts.map { it.resultId })
        assertEquals("S3", attempts.first().sessionTitle)
        assertEquals(listOf("t1"), topicAttempts(results, "Topology").map { it.answer.questionId })
    }

    @Test
    fun sessionScoresAreOldestFirstAndOnlyCountTheTopic() {
        val scores = topicSessionScores(results, "Calculus")
        assertEquals(listOf("1", "2", "3"), scores.map { it.resultId })
        assertEquals(listOf(1 to 2, 0 to 2, 1 to 1), scores.map { it.correct to it.total })
        assertEquals(listOf(50, 0, 100), scores.map { it.percent })
    }

    @Test
    fun summariesAreWeakestFirst() {
        val (topology, calculus) = topicSummaries(results)
        assertEquals("Topology", topology.topic)
        assertEquals(0, topology.percent)
        assertEquals("Calculus", calculus.topic)
        assertEquals(5, calculus.attempts)
        assertEquals(2, calculus.correct)
        assertEquals(40, calculus.percent)
        assertEquals(3, calculus.distinctQuestions)
        assertEquals(300, calculus.lastPracticedAt)
        // Blank answers don't count towards the average time: (30 + 60 + 120 + 60) / 4 s.
        assertEquals(67_500, calculus.avgTimeMs)
    }

    @Test
    fun missedMeansTheLatestAttemptWasNotCorrect() {
        // q1: wrong, then right → not missed. q2: right, then blank → missed. q3: wrong → missed.
        assertEquals(setOf("q2", "q3"), topicMissedIds(results, "Calculus"))
    }

    @Test
    fun noAnsweredQuestionsMeansNoAverageTime() {
        assertNull(topicSummaries(listOf(r("1", 1, a("q", "Logic", null)))).single().avgTimeMs)
    }
}
