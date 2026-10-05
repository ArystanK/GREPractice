package kz.arctan.grepractice.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kz.arctan.grepractice.data.newId
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question

data class PracticeConfig(
    val mode: PracticeMode,
    val title: String,
    val questions: List<Question>,
    /** null = untimed. */
    val timeLimitMs: Long?,
    /** Reveal the correct answer right after each question instead of only at the end. */
    val instantFeedback: Boolean,
    val shuffleChoices: Boolean,
)

/** A question as presented in this session; [choices] may be shuffled relative to the bank. */
class SessionItem(val question: Question, val choices: List<String>, val correctIndex: Int)

/**
 * Live state of a timed practice session. Time is measured from the wall clock, so it stays
 * correct even if the UI stops ticking for a while (e.g. on Android configuration changes).
 */
class PracticeSession(val config: PracticeConfig) {
    val items: List<SessionItem> = config.questions.map { q ->
        if (config.shuffleChoices) {
            val order = q.choices.indices.shuffled()
            SessionItem(q, order.map { q.choices[it] }, order.indexOf(q.correctIndex))
        } else {
            SessionItem(q, q.choices, q.correctIndex)
        }
    }

    val startedAt: Long = nowMillis()
    val selected = mutableStateListOf<Int?>().apply { repeat(items.size) { add(null) } }
    val flagged = mutableStateListOf<Boolean>().apply { repeat(items.size) { add(false) } }
    /** With instant feedback, a question is locked once checked. */
    val checked = mutableStateListOf<Boolean>().apply { repeat(items.size) { add(false) } }
    private val timeSpent = LongArray(items.size)

    var current by mutableIntStateOf(0)
        private set
    private var currentEnteredAt = startedAt

    /** Updated by [tick]; drives the timer display. */
    var now by mutableLongStateOf(startedAt)
        private set

    var finishedResult by mutableStateOf<PracticeResult?>(null)
        private set

    val elapsedMs: Long get() = now - startedAt
    val remainingMs: Long? get() = config.timeLimitMs?.let { (it - elapsedMs).coerceAtLeast(0) }
    val currentQuestionElapsedMs: Long get() = timeSpent[current] + (now - currentEnteredAt)
    val answeredCount: Int get() = selected.count { it != null }

    /** Returns true if time ran out and the session was auto-submitted. */
    fun tick(): Boolean {
        if (finishedResult != null) return false
        now = nowMillis()
        val limit = config.timeLimitMs
        if (limit != null && elapsedMs >= limit) {
            finish(timedOut = true)
            return true
        }
        return false
    }

    fun goTo(index: Int) {
        if (index !in items.indices || index == current) return
        val t = nowMillis()
        timeSpent[current] += t - currentEnteredAt
        currentEnteredAt = t
        current = index
        now = t
    }

    fun select(choice: Int) {
        if (checked[current]) return
        selected[current] = if (selected[current] == choice) null else choice
    }

    fun toggleFlag() {
        flagged[current] = !flagged[current]
    }

    fun check() {
        if (selected[current] != null) checked[current] = true
    }

    fun finish(timedOut: Boolean = false): PracticeResult {
        finishedResult?.let { return it }
        val t = nowMillis()
        timeSpent[current] += t - currentEnteredAt
        currentEnteredAt = t
        now = t
        val duration = config.timeLimitMs?.let { minOf(it, t - startedAt) } ?: (t - startedAt)
        val result = PracticeResult(
            id = newId(),
            mode = config.mode,
            title = config.title,
            startedAt = startedAt,
            durationMs = duration,
            timeLimitMs = config.timeLimitMs,
            timedOut = timedOut,
            answers = items.mapIndexed { i, item ->
                AnswerRecord(
                    questionId = item.question.id,
                    topic = item.question.topic,
                    questionText = item.question.text,
                    choices = item.choices,
                    correctIndex = item.correctIndex,
                    selectedIndex = selected[i],
                    timeSpentMs = timeSpent[i],
                    flagged = flagged[i],
                    explanation = item.question.explanation,
                )
            },
        )
        finishedResult = result
        return result
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}
