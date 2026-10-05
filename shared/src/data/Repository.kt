package kz.arctan.grepractice.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kotlin.random.Random

private const val QUESTIONS_FILE = "questions.json"
private const val RESULTS_FILE = "results.json"
private const val META_FILE = "meta.json"

/** Small bookkeeping file; [samplesVersion] tracks which [SampleQuestions.VERSION] the bank was seeded or upgraded with. */
@Serializable
private data class Meta(val samplesVersion: Int = 1)

internal val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

fun newId(): String = nowMillis().toString(36) + Random.nextInt(0, Int.MAX_VALUE).toString(36)

/** Per-topic aggregate over every saved answer. */
data class TopicStats(val topic: String, val answered: Int, val correct: Int, val avgTimeMs: Long) {
    val percent: Int get() = if (answered == 0) 0 else (correct * 100 + answered / 2) / answered
}

/**
 * Holds the question bank and practice history in Compose state, persisting every change to JSON
 * files in the platform data directory.
 */
class Repository {
    val questions: SnapshotStateList<Question> = mutableStateListOf()
    val results: SnapshotStateList<PracticeResult> = mutableStateListOf()

    /** Set when a data file could not be parsed; a copy of the unreadable file is kept as `<name>.broken`. */
    var loadError: String? = null
        private set

    init {
        val storedQuestions = readDataFile(QUESTIONS_FILE)
        if (storedQuestions == null) {
            questions.addAll(SampleQuestions.all)
            saveQuestions()
            saveMeta(Meta(SampleQuestions.VERSION))
        } else {
            runCatching { json.decodeFromString(ListSerializer(Question.serializer()), storedQuestions) }
                .onSuccess {
                    questions.addAll(it)
                    upgradeSamples()
                }
                .onFailure {
                    writeDataFile("$QUESTIONS_FILE.broken", storedQuestions)
                    loadError = "Could not read $QUESTIONS_FILE: ${it.message}"
                }
        }
        readDataFile(RESULTS_FILE)?.let { stored ->
            runCatching { json.decodeFromString(ListSerializer(PracticeResult.serializer()), stored) }
                .onSuccess { results.addAll(it.sortedByDescending { r -> r.startedAt }) }
                .onFailure {
                    writeDataFile("$RESULTS_FILE.broken", stored)
                    loadError = "Could not read $RESULTS_FILE: ${it.message}"
                }
        }
    }

    /**
     * Replaces sample questions from an older [SampleQuestions.VERSION] (e.g. the pre-LaTeX Unicode
     * text) with the current ones. Samples the user deleted stay deleted.
     */
    private fun upgradeSamples() {
        val meta = readDataFile(META_FILE)?.let { runCatching { json.decodeFromString(Meta.serializer(), it) }.getOrNull() } ?: Meta()
        if (meta.samplesVersion >= SampleQuestions.VERSION) return
        val current = SampleQuestions.all.associateBy { it.id }
        questions.indices.forEach { i ->
            current[questions[i].id]?.let { questions[i] = it.copy(createdAt = questions[i].createdAt) }
        }
        saveQuestions()
        saveMeta(Meta(SampleQuestions.VERSION))
    }

    private fun saveMeta(meta: Meta) {
        writeDataFile(META_FILE, json.encodeToString(Meta.serializer(), meta))
    }

    val topics: List<String>
        get() = questions.map { it.topic }.distinct().sorted()

    fun upsertQuestion(question: Question) {
        val index = questions.indexOfFirst { it.id == question.id }
        if (index >= 0) questions[index] = question else questions.add(question)
        saveQuestions()
    }

    fun deleteQuestion(id: String) {
        questions.removeAll { it.id == id }
        saveQuestions()
    }

    /** Re-adds any built-in sample questions that are missing. Returns how many were added. */
    fun restoreSamples(): Int {
        val existing = questions.map { it.id }.toSet()
        val missing = SampleQuestions.all.filter { it.id !in existing }
        questions.addAll(missing)
        saveQuestions()
        return missing.size
    }

    fun addResult(result: PracticeResult) {
        results.add(0, result)
        saveResults()
    }

    fun deleteResult(id: String) {
        results.removeAll { it.id == id }
        saveResults()
    }

    fun clearResults() {
        results.clear()
        saveResults()
    }

    fun topicStats(): List<TopicStats> =
        results.flatMap { it.answers }
            .groupBy { it.topic }
            .map { (topic, answers) ->
                TopicStats(
                    topic = topic,
                    answered = answers.size,
                    correct = answers.count { it.isCorrect },
                    avgTimeMs = answers.sumOf { it.timeSpentMs } / answers.size,
                )
            }
            .sortedBy { it.topic }

    fun exportQuestionsJson(): String = json.encodeToString(ListSerializer(Question.serializer()), questions.toList())

    /**
     * Imports questions from a JSON array. Each item needs `topic`, `text`, `choices` and either
     * `correctIndex` (0-based) or `answer` (a letter such as "C"). Items whose `id` matches an
     * existing question replace it. Returns the number of questions imported.
     */
    fun importQuestionsJson(text: String): Int {
        val items = json.decodeFromString(ListSerializer(ImportedQuestion.serializer()), text)
        val imported = items.mapIndexed { i, item ->
            val correct = item.correctIndex
                ?: item.answer?.trim()?.uppercase()?.singleOrNull()?.let { it - 'A' }
                ?: error("Question #${i + 1}: needs \"correctIndex\" or \"answer\"")
            require(item.text.isNotBlank()) { "Question #${i + 1}: \"text\" is empty" }
            require(item.choices.size >= 2) { "Question #${i + 1}: needs at least 2 choices" }
            require(correct in item.choices.indices) { "Question #${i + 1}: correct answer is out of range" }
            Question(
                id = item.id ?: newId(),
                topic = item.topic.ifBlank { "General" },
                text = item.text,
                choices = item.choices,
                correctIndex = correct,
                explanation = item.explanation,
                createdAt = nowMillis(),
            )
        }
        imported.forEach { q ->
            val index = questions.indexOfFirst { it.id == q.id }
            if (index >= 0) questions[index] = q else questions.add(q)
        }
        saveQuestions()
        return imported.size
    }

    private fun saveQuestions() {
        writeDataFile(QUESTIONS_FILE, exportQuestionsJson())
    }

    private fun saveResults() {
        writeDataFile(RESULTS_FILE, json.encodeToString(ListSerializer(PracticeResult.serializer()), results.toList()))
    }
}

@Serializable
private data class ImportedQuestion(
    val id: String? = null,
    val topic: String = "",
    val text: String,
    val choices: List<String>,
    val correctIndex: Int? = null,
    val answer: String? = null,
    val explanation: String = "",
)
