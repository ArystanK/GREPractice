package kz.arctan.grepractice.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kz.arctan.grepractice.data.cloud.CloudDoc
import kz.arctan.grepractice.data.cloud.CloudWrite
import kz.arctan.grepractice.data.cloud.QUESTIONS_COLLECTION
import kz.arctan.grepractice.data.cloud.RESULTS_COLLECTION
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.isPracticeTestQuestion
import kotlin.random.Random

private const val QUESTIONS_FILE = "questions.json"
private const val RESULTS_FILE = "results.json"
private const val META_FILE = "meta.json"

/**
 * Small bookkeeping file. [samplesVersion] tracks which [SampleQuestions.VERSION] the bank was
 * seeded or upgraded with; the tombstones map deleted ids to deletion time so deletes reach the cloud.
 */
@Serializable
private data class Meta(
    val samplesVersion: Int = 1,
    val deletedQuestions: Map<String, Long> = emptyMap(),
    val deletedResults: Map<String, Long> = emptyMap(),
    /** Per account uid: images already uploaded, so each is sent once. */
    val uploadedImages: Map<String, Set<String>> = emptyMap(),
)

internal val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/** Compact encoding for cloud payloads. */
private val syncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun newId(): String = nowMillis().toString(36) + Random.nextInt(0, Int.MAX_VALUE).toString(36)

/** Per-topic aggregate over every saved answer. */
data class TopicStats(val topic: String, val answered: Int, val correct: Int, val avgTimeMs: Long) {
    val percent: Int get() = if (answered == 0) 0 else (correct * 100 + answered / 2) / answered
}

/** What [Repository.importQuestionsJson] did. */
data class ImportSummary(val imported: Int, val skippedDuplicates: Int)

private val Whitespace = Regex("""\s+""")

private fun normalized(s: String) = s.replace(Whitespace, "").lowercase()

/** Identity of a question's content, for finding the same question stored under different ids. */
private fun contentKey(q: Question) = Triple(normalized(q.text), q.choices.map(::normalized), q.correctIndex)

/** Which duplicate to keep: practice-test questions, then ones with an explanation, then the oldest. */
private val KeepOrder = compareBy<Question>(
    { !isPracticeTestQuestion(it.id) },
    { it.explanation.isBlank() },
    { it.createdAt },
    { it.id },
)

/** Outcome of [Repository.mergeRemote]: what changed locally and what must be uploaded. */
class SyncPlan(val downloaded: Int, val uploads: List<CloudWrite>)

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

    /** Incremented on every local change (not on changes pulled from the cloud); drives auto-sync. */
    var version by mutableIntStateOf(0)
        private set

    private var meta: Meta = readDataFile(META_FILE)
        ?.let { runCatching { json.decodeFromString(Meta.serializer(), it) }.getOrNull() }
        ?: Meta()

    init {
        val storedQuestions = readDataFile(QUESTIONS_FILE)
        if (storedQuestions == null) {
            questions.addAll(SampleQuestions.all)
            saveQuestions()
            saveMeta(meta.copy(samplesVersion = SampleQuestions.VERSION))
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
        if (meta.samplesVersion >= SampleQuestions.VERSION) return
        val current = SampleQuestions.all.associateBy { it.id }
        val now = nowMillis()
        questions.indices.forEach { i ->
            current[questions[i].id]?.let { questions[i] = it.copy(createdAt = questions[i].createdAt, updatedAt = now) }
        }
        saveQuestions()
        saveMeta(meta.copy(samplesVersion = SampleQuestions.VERSION))
    }

    private fun saveMeta(newMeta: Meta) {
        meta = newMeta
        writeDataFile(META_FILE, json.encodeToString(Meta.serializer(), newMeta))
    }

    val topics: List<String>
        get() = questions.map { it.topic }.distinct().sorted()

    fun upsertQuestion(question: Question) {
        putQuestion(question.copy(updatedAt = nowMillis()))
        saveQuestions()
        localChange()
    }

    fun deleteQuestion(id: String) = deleteQuestions(setOf(id))

    private fun deleteQuestions(ids: Set<String>) {
        if (ids.isEmpty()) return
        val now = nowMillis()
        questions.removeAll { it.id in ids }
        saveQuestions()
        saveMeta(meta.copy(deletedQuestions = meta.deletedQuestions + ids.associateWith { now }))
        localChange()
    }

    /**
     * Groups of questions that are the same question under different ids: identical text, choices
     * and correct answer, ignoring whitespace and letter case. The question to keep comes first.
     */
    fun duplicateGroups(): List<List<Question>> =
        questions.groupBy(::contentKey).values
            .filter { it.size > 1 }
            .map { it.sortedWith(KeepOrder) }

    /** Deletes all but the first question of each [duplicateGroups] group (deletions sync). Returns how many were removed. */
    fun removeDuplicates(): Int {
        val groups = duplicateGroups()
        groups.forEach { group ->
            // Don't lose an explanation that only a removed copy had.
            val keep = group.first()
            val explanation = group.firstOrNull { it.explanation.isNotBlank() }?.explanation
            if (keep.explanation.isBlank() && explanation != null) putQuestion(keep.copy(explanation = explanation, updatedAt = nowMillis()))
        }
        val removed = groups.flatMap { it.drop(1) }.map { it.id }.toSet()
        deleteQuestions(removed)
        return removed.size
    }

    /** Re-adds any built-in sample questions that are missing. Returns how many were added. */
    fun restoreSamples(): Int {
        val existing = questions.map { it.id }.toSet()
        val now = nowMillis()
        val missing = SampleQuestions.all.filter { it.id !in existing }
        missing.forEach { putQuestion(it.copy(updatedAt = now)) }
        saveQuestions()
        if (missing.isNotEmpty()) localChange()
        return missing.size
    }

    fun addResult(result: PracticeResult) {
        results.add(0, result)
        saveResults()
        localChange()
    }

    fun deleteResult(id: String) {
        results.removeAll { it.id == id }
        saveResults()
        saveMeta(meta.copy(deletedResults = meta.deletedResults + (id to nowMillis())))
        localChange()
    }

    fun clearResults() {
        val now = nowMillis()
        val ids = results.map { it.id }
        results.clear()
        saveResults()
        saveMeta(meta.copy(deletedResults = meta.deletedResults + ids.associateWith { now }))
        localChange()
    }

    /** Every image referenced by a question or a saved result. */
    fun referencedImages(): Set<String> =
        (questions.flatMap { it.imageRefs() } +
            results.flatMap { r -> r.answers.flatMap { a -> (listOf(a.questionText, a.explanation) + a.choices).flatMap(::imageRefs) } })
            .toSet()

    fun isImageUploaded(uid: String, name: String): Boolean = name in meta.uploadedImages[uid].orEmpty()

    fun markImageUploaded(uid: String, name: String) {
        if (isImageUploaded(uid, name)) return
        saveMeta(meta.copy(uploadedImages = meta.uploadedImages + (uid to (meta.uploadedImages[uid].orEmpty() + name))))
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
     * existing question replace it. A question that's already in the bank under another id (see
     * [duplicateGroups]) is skipped, unless the new copy is the one to keep (e.g. a practice-test id
     * replacing a random-id copy), in which case the old copy is deleted.
     */
    fun importQuestionsJson(text: String): ImportSummary {
        val items = json.decodeFromString(ListSerializer(ImportedQuestion.serializer()), text)
        val now = nowMillis()
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
                createdAt = now,
                updatedAt = now,
            )
        }
        val byContent = questions.groupBy(::contentKey).mapValues { it.value.toMutableList() }.toMutableMap()
        val replaced = mutableSetOf<String>()
        var skipped = 0
        val accepted = imported.filter { q ->
            val copies = byContent.getOrPut(contentKey(q)) { mutableListOf() }
            val others = copies.filter { it.id != q.id }
            val accept = others.isEmpty() || KeepOrder.compare(q, others.minWith(KeepOrder)) < 0
            if (accept) {
                replaced += others.map { it.id }
                copies.clear()
                copies += q
            } else {
                skipped++
            }
            accept
        }
        accepted.forEach(::putQuestion)
        saveQuestions()
        if (replaced.isNotEmpty()) deleteQuestions(replaced) else localChange()
        return ImportSummary(imported = accepted.size, skippedDuplicates = skipped)
    }

    /**
     * Two-way merge with the cloud copy. Questions: the most recently edited version wins, whether
     * it's an edit or a deletion. Results never change after they're saved, so they're merged as a
     * union, and a deletion on either side wins. Remote winners are applied locally right away; the
     * returned plan lists the local winners that must be uploaded.
     */
    fun mergeRemote(remoteQuestions: List<CloudDoc>, remoteResults: List<CloudDoc>): SyncPlan {
        var downloaded = 0
        val uploads = mutableListOf<CloudWrite>()
        val deletedQuestions = meta.deletedQuestions.toMutableMap()
        val deletedResults = meta.deletedResults.toMutableMap()

        // ---- Questions ----
        val localQuestions = questions.associateBy { it.id }
        val remoteQ = remoteQuestions.associateBy { it.id }
        for (id in localQuestions.keys + deletedQuestions.keys + remoteQ.keys) {
            val local = localQuestions[id]
            val localTime = local?.updatedAt ?: deletedQuestions[id]
            val remote = remoteQ[id]
            when {
                remote != null && (localTime == null || remote.updatedAt > localTime) -> {
                    if (remote.deleted) {
                        if (local != null) {
                            questions.removeAll { it.id == id }
                            downloaded++
                        }
                        deletedQuestions[id] = remote.updatedAt
                    } else {
                        val q = runCatching { syncJson.decodeFromString(Question.serializer(), remote.payload) }.getOrNull() ?: continue
                        putQuestion(q.copy(updatedAt = remote.updatedAt))
                        deletedQuestions.remove(id)
                        downloaded++
                    }
                }
                localTime != null && (remote == null || remote.updatedAt < localTime) -> {
                    uploads += if (local != null) {
                        CloudWrite(QUESTIONS_COLLECTION, CloudDoc(id, syncJson.encodeToString(Question.serializer(), local), local.updatedAt, false))
                    } else {
                        CloudWrite(QUESTIONS_COLLECTION, CloudDoc(id, "", localTime, true))
                    }
                }
            }
        }

        // ---- Results ----
        val localResults = results.associateBy { it.id }
        val remoteR = remoteResults.associateBy { it.id }
        for (doc in remoteResults.filter { it.deleted }) {
            if (doc.id in localResults) {
                results.removeAll { it.id == doc.id }
                downloaded++
            }
            deletedResults[doc.id] = doc.updatedAt
        }
        for (doc in remoteResults.filter { !it.deleted && it.id !in localResults }) {
            if (doc.id in deletedResults) continue
            val r = runCatching { syncJson.decodeFromString(PracticeResult.serializer(), doc.payload) }.getOrNull() ?: continue
            results.add(r)
            downloaded++
        }
        for ((id, deletedAt) in deletedResults) {
            if (remoteR[id]?.deleted != true) uploads += CloudWrite(RESULTS_COLLECTION, CloudDoc(id, "", deletedAt, true))
        }
        for (r in localResults.values) {
            if (r.id !in remoteR && r.id !in deletedResults) {
                uploads += CloudWrite(RESULTS_COLLECTION, CloudDoc(r.id, syncJson.encodeToString(PracticeResult.serializer(), r), r.startedAt, false))
            }
        }

        if (downloaded > 0) {
            results.sortByDescending { it.startedAt }
            saveQuestions()
            saveResults()
        }
        if (deletedQuestions != meta.deletedQuestions || deletedResults != meta.deletedResults) {
            saveMeta(meta.copy(deletedQuestions = deletedQuestions, deletedResults = deletedResults))
        }
        return SyncPlan(downloaded, uploads)
    }

    private fun putQuestion(q: Question) {
        val index = questions.indexOfFirst { it.id == q.id }
        if (index >= 0) questions[index] = q else questions.add(q)
        if (q.id in meta.deletedQuestions) saveMeta(meta.copy(deletedQuestions = meta.deletedQuestions - q.id))
    }

    private fun localChange() {
        version++
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
