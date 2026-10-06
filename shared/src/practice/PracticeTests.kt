package kz.arctan.grepractice.practice

import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.Question

/** Question ids like `practice2-07`: question 7 of practice test 2. */
private val PracticeId = Regex("""practice(\d+)-(\d+)""")

/** A full practice test assembled from the bank's `practice<test>-<number>` questions, in order. */
data class PracticeTest(val number: Int, val questions: List<Question>) {
    val id: String get() = "practice$number"
    val title: String get() = "Practice test $number"

    /** The real exam's 170 minutes for 66 questions, scaled if a test has a different length. */
    val timeLimitMs: Long get() = Gre.EXAM_MINUTES * 60_000L * questions.size / Gre.EXAM_QUESTIONS

    fun config(timed: Boolean) = PracticeConfig(
        mode = PracticeMode.EXAM,
        title = title,
        questions = questions,
        timeLimitMs = if (timed) timeLimitMs else null,
        // Timed runs reveal answers only at the end, like the real test.
        instantFeedback = !timed,
        shuffleChoices = false,
        testId = id,
    )
}

fun isPracticeTestQuestion(questionId: String): Boolean = PracticeId.matches(questionId)

/** Groups practice-test questions by test number; questions within a test are ordered by number. */
fun findPracticeTests(questions: List<Question>): List<PracticeTest> =
    questions
        .mapNotNull { q -> PracticeId.matchEntire(q.id)?.let { m -> Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), q) } }
        .groupBy({ it.first }, { it.second to it.third })
        .map { (number, items) -> PracticeTest(number, items.sortedBy { it.first }.map { it.second }) }
        .sortedBy { it.number }
