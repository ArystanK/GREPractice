package kz.arctan.grepractice

import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.GreScores
import kz.arctan.grepractice.practice.STRESS_MARGIN
import kz.arctan.grepractice.practice.ScoreScale
import kz.arctan.grepractice.practice.ScoringRule
import kz.arctan.grepractice.practice.formulaRawScore
import kz.arctan.grepractice.practice.practiceTestScore
import kz.arctan.grepractice.practice.predictExamScore
import kz.arctan.grepractice.practice.predictedScaled
import kz.arctan.grepractice.practice.predictedSessionScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GreScoreTest {
    @Test
    fun everyTableCoversEachRawScoreOnceAndNeverDecreases() {
        GreScores.conversions.forEach { c ->
            val covered = c.rows.flatMap { it.rawMin..it.rawMax }.sorted()
            assertEquals((0..66).toList(), covered, c.form)
            val byRaw = (0..66).map(c::scaled)
            assertEquals(byRaw.sorted(), byRaw, "${c.form} scaled scores must rise with the raw score")
        }
    }

    @Test
    fun matchesTheWorkedExamplesInTheBooks() {
        // Each book's own example: (right, wrong) → raw → scaled.
        val examples = listOf(
            Triple("practice1", 46 to 9, 44 to 770),
            Triple("practice2", 34 to 15, 30 to 640),
            Triple("practice3", 48 to 15, 44 to 870),
            Triple("practice4", 40 to 10, 38 to 780),
            Triple("practice5", 48 to 15, 44 to 710),
        )
        examples.forEach { (test, answers, expected) ->
            val raw = formulaRawScore(answers.first, answers.second)
            assertEquals(expected.first, raw, test)
            assertEquals(expected.second, GreScores.conversionFor(test)!!.scaled(raw), test)
        }
        assertEquals(0, formulaRawScore(1, 20)) // never negative
        // The current book counts right answers only: its example gives 800 for 50 right.
        val current = GreScores.conversionFor("practice6")!!
        assertEquals(ScoringRule.RightOnly, current.rule)
        assertEquals(50, current.rawScore(50, 16))
        assertEquals(800, current.scaled(50))
    }

    @Test
    fun scoresTheNewTestsByRightAnswersOnly() {
        val official = practiceTestScore(practiceResult("practice6", correct = 50, wrong = 10))!!
        assertEquals(50, official.raw)
        assertEquals(800, official.scaled)
        assertEquals(800, official.currentScaleEquivalent)

        // The unofficial test's chart is its author's estimate, with percentiles.
        val unofficial = practiceTestScore(practiceResult("practice7", correct = 55, wrong = 11))!!
        assertEquals(870, unofficial.scaled)
        assertEquals(89, unofficial.percentBelow)
        assertFalse(unofficial.conversion.official)
    }

    @Test
    fun scoresAPracticeTestWithItsOwnTable() {
        // 56 right, 10 wrong on practice 1: 56 - 2.5 = 53.5 → 54 → 870.
        val s = practiceTestScore(practiceResult("practice1", correct = 56, wrong = 10))!!
        assertEquals(54, s.raw)
        assertEquals(870, s.scaled)
        assertNull(s.percentBelow) // GR1268's table prints no percentages
        assertEquals(870, s.currentScaleEquivalent)

        // Pre-2001 forms keep their printed score but are compared on today's scale by raw score.
        val old = practiceTestScore(practiceResult("practice3", correct = 48, wrong = 15))!!
        assertEquals(870, old.scaled)
        assertEquals(64, old.percentBelow)
        assertEquals(ScoreScale.Pre2001, old.conversion.scale)
        assertEquals(GreScores.referenceScaled(48), old.currentScaleEquivalent) // its 48 right answers today
        assertTrue(old.currentScaleEquivalent < old.scaled)

        assertNull(practiceTestScore(practiceResult(null, correct = 50, wrong = 0)))
    }

    @Test
    fun predictsAFullExamFromRandomQuestions() {
        assertNull(predictedScaled(5, 9)) // too few answers
        // 8 right of 10 → 52.8 of 66 → 53 right → 830 by the current official table.
        assertEquals(830, predictedScaled(8, 10))
        val session = practiceResult(null, correct = 8, wrong = 2)
        assertEquals(830, predictedSessionScore(session))
        // Practice tests get their real score instead.
        assertNull(predictedSessionScore(practiceResult("practice2", correct = 40, wrong = 5)))
    }

    @Test
    fun predictionsUseTheCurrentOfficialTable() {
        assertEquals("GR3768", GreScores.reference.form)
        assertEquals(830, GreScores.referenceScaled(54))
        assertEquals(53, GreScores.referenceRaw(830)) // 53 and 54 both earn 830
        assertEquals(970, GreScores.referenceScaled(66))
        assertEquals(GreScores.referenceScaled(66), GreScores.referenceScaled(80))
    }

    @Test
    fun examPredictionCombinesTestsAndTopicsAndAllowsForStress() {
        val bank = (1..66).map { Question("practice1-$it", if (it <= 33) "Calculus" else "Topology", "q", listOf("a", "b"), 0) }
        assertNull(predictExamScore(emptyList(), bank))

        val test = practiceResult("practice1", correct = 56, wrong = 10, at = 2)
        val p = assertNotNull(predictExamScore(listOf(test), bank))
        assertEquals(870, p.practiceTestScore)
        assertEquals(1, p.practiceTests)
        assertTrue(p.score < p.beforeStress, "stress lowers the prediction")
        val stressedRaw = Math.round(GreScores.referenceRaw(p.beforeStress) * (1 - STRESS_MARGIN)).toInt()
        assertEquals(GreScores.referenceScaled(stressedRaw), p.score)
        assertEquals(66, p.answered)

        // Weak calculus answers on top pull the topic estimate, and the prediction, down.
        val weakCalculus = PracticeResult(
            "r2", PracticeMode.TOPIC, "Calculus", startedAt = 1, durationMs = 0,
            answers = (1..30).map { AnswerRecord("practice1-${it % 33 + 1}", "Calculus", "q", listOf("a", "b"), 0, selectedIndex = 1) },
        )
        val worse = predictExamScore(listOf(test, weakCalculus), bank)!!
        assertTrue(worse.topicScore < p.topicScore)
        assertTrue(worse.score < p.score)
        assertEquals(listOf("Calculus"), worse.weakTopics)
    }

    @Test
    fun withoutPracticeTestsItNeedsEnoughAnswers() {
        val bank = listOf(Question("q", "Calculus", "q", listOf("a", "b"), 0))
        assertNull(predictExamScore(listOf(practiceResult(null, correct = 10, wrong = 5)), bank))
        val p = predictExamScore(listOf(practiceResult(null, correct = 15, wrong = 5)), bank)!!
        assertNull(p.practiceTestScore)
        assertEquals(0, p.practiceTests)
    }

    private fun practiceResult(testId: String?, correct: Int, wrong: Int, at: Long = 0): PracticeResult {
        val total = if (testId != null) 66 else correct + wrong
        val answers = (0 until total).map { i ->
            val selected = when {
                i < correct -> 0
                i < correct + wrong -> 1
                else -> null
            }
            // A practice test splits its questions between two topics, like the test bank above.
            val topic = if (testId != null && i >= 33) "Topology" else "Calculus"
            AnswerRecord("${testId ?: "q"}-$i", topic, "q", listOf("a", "b"), 0, selectedIndex = selected)
        }
        return PracticeResult("r-$testId-$at", PracticeMode.EXAM, "t", startedAt = at, durationMs = 0, answers = answers, testId = testId)
    }
}
