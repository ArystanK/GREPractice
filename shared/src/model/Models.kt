package kz.arctan.grepractice.model

import kotlinx.serialization.Serializable

/** A multiple-choice question in the bank. [correctIndex] is 0-based into [choices]. */
@Serializable
data class Question(
    val id: String,
    val topic: String,
    val text: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val createdAt: Long = 0L,
    /** Last local edit (epoch millis); cloud sync keeps the most recently edited version. */
    val updatedAt: Long = 0L,
)

enum class PracticeMode(val label: String) {
    EXAM("Simulated exam"),
    TOPIC("Topic practice"),
    RANDOM("Random question"),
    RETRY("Retry incorrect"),
}

/** Snapshot of one answered question, so a saved result stays readable even if the bank changes later. */
@Serializable
data class AnswerRecord(
    val questionId: String,
    val topic: String,
    val questionText: String,
    /** Choices in the order they were shown. */
    val choices: List<String>,
    val correctIndex: Int,
    val selectedIndex: Int? = null,
    val timeSpentMs: Long = 0L,
    val flagged: Boolean = false,
    val explanation: String = "",
) {
    val isCorrect: Boolean get() = selectedIndex == correctIndex
    val isAnswered: Boolean get() = selectedIndex != null
}

@Serializable
data class PracticeResult(
    val id: String,
    val mode: PracticeMode,
    val title: String,
    val startedAt: Long,
    val durationMs: Long,
    /** null when untimed. */
    val timeLimitMs: Long? = null,
    val timedOut: Boolean = false,
    val answers: List<AnswerRecord>,
) {
    val correct: Int get() = answers.count { it.isCorrect }
    val total: Int get() = answers.size
    val percent: Int get() = if (total == 0) 0 else (correct * 100 + total / 2) / total
}

object Gre {
    /** The GRE Mathematics Subject Test: 66 questions in 2 hours 50 minutes. */
    const val EXAM_QUESTIONS = 66
    const val EXAM_MINUTES = 170
    const val PACE_MS_PER_QUESTION: Long = EXAM_MINUTES * 60_000L / EXAM_QUESTIONS

    val defaultTopics = listOf(
        "Calculus",
        "Differential Equations",
        "Linear Algebra",
        "Abstract Algebra",
        "Number Theory",
        "Real Analysis",
        "Complex Analysis",
        "Topology",
        "Discrete Math",
        "Probability & Statistics",
        "Geometry",
        "Numerical Analysis",
    )

    fun letter(index: Int): String = ('A' + index).toString()
}
