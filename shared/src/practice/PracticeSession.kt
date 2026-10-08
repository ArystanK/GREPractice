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
import kotlinx.serialization.Serializable

data class PracticeConfig(
    val mode: PracticeMode,
    val title: String,
    val questions: List<Question>,
    /** null = untimed. */
    val timeLimitMs: Long?,
    /** Reveal the correct answer right after each question instead of only at the end. */
    val instantFeedback: Boolean,
    val shuffleChoices: Boolean,
    /** The practice test this session runs, if any. */
    val testId: String? = null,
)

/** A question as presented in this session; [choices] may be shuffled relative to the bank. */
class SessionItem(val question: Question, val choices: List<String>, val correctIndex: Int)

/** A running session as stored on disk, so it can be resumed after the app's process ends. */
@Serializable
data class SavedSession(
    val mode: PracticeMode,
    val title: String,
    val timeLimitMs: Long?,
    val instantFeedback: Boolean,
    val shuffleChoices: Boolean,
    val testId: String? = null,
    val startedAt: Long,
    val current: Int,
    val currentEnteredAt: Long,
    val items: List<SavedItem>,
)

/** One question of a [SavedSession], with the questions kept as shown, so edits made meanwhile don't change it. */
@Serializable
data class SavedItem(
    val question: Question,
    val choices: List<String>,
    val correctIndex: Int,
    val selected: Int? = null,
    val flagged: Boolean = false,
    val checked: Boolean = false,
    val timeSpentMs: Long = 0,
)

/**
 * Live state of a timed practice session. Time is measured from the wall clock, so it stays
 * correct even if the UI stops ticking for a while (e.g. on Android configuration changes), and
 * a restored session's clock has kept running while the app was closed, as in a real exam.
 */
class PracticeSession private constructor(
    val config: PracticeConfig,
    val items: List<SessionItem>,
    val startedAt: Long,
) {
    constructor(config: PracticeConfig) : this(config, presentedItems(config), nowMillis())

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
            testId = config.testId,
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

    /** The state to store; reads only what answering and navigating change, not the ticking clock. */
    fun snapshot(): SavedSession = SavedSession(
        mode = config.mode,
        title = config.title,
        timeLimitMs = config.timeLimitMs,
        instantFeedback = config.instantFeedback,
        shuffleChoices = config.shuffleChoices,
        testId = config.testId,
        startedAt = startedAt,
        current = current,
        currentEnteredAt = currentEnteredAt,
        items = items.mapIndexed { i, item ->
            SavedItem(item.question, item.choices, item.correctIndex, selected[i], flagged[i], checked[i], timeSpent[i])
        },
    )

    companion object {
        private fun presentedItems(config: PracticeConfig): List<SessionItem> = config.questions.map { q ->
            if (config.shuffleChoices) {
                val order = q.choices.indices.shuffled()
                SessionItem(q, order.map { q.choices[it] }, order.indexOf(q.correctIndex))
            } else {
                SessionItem(q, q.choices, q.correctIndex)
            }
        }

        /** The session [saved] by [snapshot], or null if it is inconsistent (e.g. a damaged file). */
        fun restore(saved: SavedSession): PracticeSession? {
            val valid = saved.items.isNotEmpty() && saved.current in saved.items.indices && saved.items.all { item ->
                item.correctIndex in item.choices.indices && (item.selected == null || item.selected in item.choices.indices)
            }
            if (!valid) return null
            val config = PracticeConfig(
                mode = saved.mode,
                title = saved.title,
                questions = saved.items.map { it.question },
                timeLimitMs = saved.timeLimitMs,
                instantFeedback = saved.instantFeedback,
                shuffleChoices = saved.shuffleChoices,
                testId = saved.testId,
            )
            val items = saved.items.map { SessionItem(it.question, it.choices, it.correctIndex) }
            return PracticeSession(config, items, saved.startedAt).apply {
                saved.items.forEachIndexed { i, item ->
                    selected[i] = item.selected
                    flagged[i] = item.flagged
                    checked[i] = item.checked
                    timeSpent[i] = item.timeSpentMs
                }
                current = saved.current
                currentEnteredAt = saved.currentEnteredAt
                now = nowMillis()
            }
        }
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
