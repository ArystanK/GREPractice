package kz.arctan.grepractice.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import kz.arctan.grepractice.practice.SavedSession
import kz.arctan.grepractice.practice.isPracticeTestQuestion
import kotlin.random.Random

private const val QUESTIONS_FILE = "questions.json"
private const val BANK_FILE = "bank.json"
private const val RESULTS_FILE = "results.json"
private const val META_FILE = "meta.json"
/** Not "session.json": the desktop app keeps its sign-in there. */
private const val ACTIVE_SESSION_FILE = "practice-session.json"

/** Key in [Meta.uploadedImages] for figures uploaded to the shared bank (rather than to an account). */
const val SHARED_IMAGES_KEY = "shared-bank"

/**
 * Small bookkeeping file. [samplesVersion] tracks which [SampleQuestions.VERSION] the own questions
 * were seeded or upgraded with; the tombstones map deleted ids to deletion time so deletes reach the
 * cloud; [bankSyncedUpTo] is the newest shared-bank change already downloaded.
 */
@Serializable
private data class Meta(
    val samplesVersion: Int = 1,
    val deletedQuestions: Map<String, Long> = emptyMap(),
    val deletedResults: Map<String, Long> = emptyMap(),
    /** Per account uid (or [SHARED_IMAGES_KEY]): images already uploaded, so each is sent once. */
    val uploadedImages: Map<String, Set<String>> = emptyMap(),
    val bankSyncedUpTo: Long = 0L,
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

internal fun encodeQuestion(q: Question): String = syncJson.encodeToString(Question.serializer(), q)

internal fun decodeQuestion(payload: String): Question? =
    runCatching { syncJson.decodeFromString(Question.serializer(), payload) }.getOrNull()

fun newId(): String = nowMillis().toString(36) + Random.nextInt(0, Int.MAX_VALUE).toString(36)

/** Per-topic aggregate over every saved answer. */
data class TopicStats(val topic: String, val answered: Int, val correct: Int, val avgTimeMs: Long) {
    val percent: Int get() = if (answered == 0) 0 else (correct * 100 + answered / 2) / answered
}

/**
 * What [Repository.importQuestionsJson] did. [shared] are imported questions whose id belongs to the
 * shared bank: they weren't added to the user's own questions, and only an admin can apply them.
 */
data class ImportSummary(val imported: Int, val skippedDuplicates: Int, val shared: List<Question> = emptyList())

private val Whitespace = Regex("""\s+""")

private fun normalized(s: String) = s.replace(Whitespace, "").lowercase()

/** Identity of a question's content, for finding the same question stored under different ids. */
private fun contentKey(q: Question) = Triple(normalized(q.text), q.choices.map(::normalized), q.correctIndex)

/** Outcome of [Repository.mergeRemote]: what changed locally and what must be uploaded. */
class SyncPlan(val downloaded: Int, val uploads: List<CloudWrite>)

/**
 * Holds the questions and practice history in Compose state, persisting every change to JSON files
 * in the platform data directory.
 *
 * Questions come from two places: the common **shared bank** (read-only except for admins, cached in
 * `bank.json`) and the user's **own** questions (`questions.json`, synced to their account only).
 * [questions] is the combined view; when both have the same id, the shared copy wins.
 */
class Repository {
    /** Shared bank plus the user's own questions. Mutate through the methods below, never directly. */
    val questions: SnapshotStateList<Question> = mutableStateListOf()
    val results: SnapshotStateList<PracticeResult> = mutableStateListOf()

    private val own = mutableListOf<Question>()
    private val shared = LinkedHashMap<String, Question>()

    /** Ids in the shared bank; drives "Shared" badges and read-only editing. */
    var sharedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Set when a data file could not be parsed; a copy of the unreadable file is kept as `<name>.broken`. */
    var loadError: String? = null
        private set

    /** Incremented on every local change to own data (not on changes pulled from the cloud); drives auto-sync. */
    var version by mutableIntStateOf(0)
        private set

    private var meta: Meta = readDataFile(META_FILE)
        ?.let { runCatching { json.decodeFromString(Meta.serializer(), it) }.getOrNull() }
        ?: Meta()

    /** Which duplicate to keep: shared-bank questions, practice-test ids, ones with an explanation, then the oldest. */
    private val keepOrder = compareBy<Question>(
        { it.id !in shared },
        { !isPracticeTestQuestion(it.id) },
        { it.explanation.isBlank() },
        { it.createdAt },
        { it.id },
    )

    init {
        val storedQuestions = readDataFile(QUESTIONS_FILE)
        if (storedQuestions == null) {
            own.addAll(SampleQuestions.all)
            saveOwn()
            saveMeta(meta.copy(samplesVersion = SampleQuestions.VERSION))
        } else {
            runCatching { json.decodeFromString(ListSerializer(Question.serializer()), storedQuestions) }
                .onSuccess {
                    own.addAll(it)
                    upgradeSamples()
                }
                .onFailure {
                    writeDataFile("$QUESTIONS_FILE.broken", storedQuestions)
                    loadError = "Could not read $QUESTIONS_FILE: ${it.message}"
                }
        }
        readDataFile(BANK_FILE)?.let { stored ->
            runCatching { json.decodeFromString(ListSerializer(Question.serializer()), stored) }
                .onSuccess { list -> list.forEach { shared[it.id] = it } }
                // The bank is only a cache: drop it and download it again.
                .onFailure { saveMeta(meta.copy(bankSyncedUpTo = 0L)) }
        }
        readDataFile(RESULTS_FILE)?.let { stored ->
            runCatching { json.decodeFromString(ListSerializer(PracticeResult.serializer()), stored) }
                .onSuccess { results.addAll(it.sortedByDescending { r -> r.startedAt }) }
                .onFailure {
                    writeDataFile("$RESULTS_FILE.broken", stored)
                    loadError = "Could not read $RESULTS_FILE: ${it.message}"
                }
        }
        rebuild()
    }

    /**
     * Replaces sample questions from an older [SampleQuestions.VERSION] (e.g. the pre-LaTeX Unicode
     * text) with the current ones. Samples the user deleted stay deleted.
     */
    private fun upgradeSamples() {
        if (meta.samplesVersion >= SampleQuestions.VERSION) return
        val current = SampleQuestions.all.associateBy { it.id }
        val now = nowMillis()
        own.indices.forEach { i ->
            current[own[i].id]?.let { own[i] = it.copy(createdAt = own[i].createdAt, updatedAt = now) }
        }
        saveOwn()
        saveMeta(meta.copy(samplesVersion = SampleQuestions.VERSION))
    }

    private fun saveMeta(newMeta: Meta) {
        meta = newMeta
        writeDataFile(META_FILE, json.encodeToString(Meta.serializer(), newMeta))
    }

    /** Recomputes [questions]: shared bank first, then own questions not shadowed by a shared id. */
    private fun rebuild() {
        val combined = shared.values + own.filter { it.id !in shared }
        questions.clear()
        questions.addAll(combined)
        sharedIds = shared.keys.toSet()
    }

    val topics: List<String>
        get() = questions.map { it.topic }.distinct().sorted()

    fun isShared(id: String): Boolean = id in sharedIds

    /** The user's own questions (excluding ones shadowed by the shared bank). */
    fun ownQuestions(): List<Question> = own.filter { it.id !in shared }

    // ---- Own questions ----

    fun upsertQuestion(question: Question) {
        putOwn(question.copy(updatedAt = nowMillis()))
        saveOwn()
        rebuild()
        localChange()
    }

    fun deleteQuestion(id: String) = deleteOwn(setOf(id))

    private fun deleteOwn(ids: Set<String>) {
        val present = ids.filter { id -> own.any { it.id == id } }.toSet()
        if (present.isEmpty()) return
        val now = nowMillis()
        own.removeAll { it.id in present }
        saveOwn()
        saveMeta(meta.copy(deletedQuestions = meta.deletedQuestions + present.associateWith { now }))
        rebuild()
        localChange()
    }

    /**
     * Groups of questions that are the same question under different ids: identical text, choices
     * and correct answer, ignoring whitespace and letter case. The question to keep comes first.
     */
    fun duplicateGroups(): List<List<Question>> =
        questions.groupBy(::contentKey).values
            .filter { it.size > 1 }
            .map { it.sortedWith(keepOrder) }

    /**
     * Deletes the user's own copies of questions in [duplicateGroups] (deletions sync), keeping the
     * first of each group. Shared-bank questions are never removed here. Returns how many were removed.
     */
    fun removeDuplicates(): Int {
        val groups = duplicateGroups()
        groups.forEach { group ->
            // Don't lose an explanation that only a removed copy had.
            val keep = group.first()
            val explanation = group.firstOrNull { it.explanation.isNotBlank() }?.explanation
            if (keep.id !in shared && keep.explanation.isBlank() && explanation != null) {
                putOwn(keep.copy(explanation = explanation, updatedAt = nowMillis()))
            }
        }
        saveOwn()
        val removed = groups.flatMap { it.drop(1) }.map { it.id }.filter { it !in shared }.toSet()
        deleteOwn(removed)
        rebuild()
        return removed.size
    }

    /** Re-adds any built-in sample questions that are missing. Returns how many were added. */
    fun restoreSamples(): Int {
        val existing = questions.map { it.id }.toSet()
        val now = nowMillis()
        val missing = SampleQuestions.all.filter { it.id !in existing }
        missing.forEach { putOwn(it.copy(updatedAt = now)) }
        saveOwn()
        rebuild()
        if (missing.isNotEmpty()) localChange()
        return missing.size
    }

    // ---- Shared bank ----

    /** Newest shared-bank change already downloaded (epoch millis); the next sync asks only for newer ones. */
    val sharedSyncedUpTo: Long get() = meta.bankSyncedUpTo

    /** Applies shared-bank changes from the cloud. Returns how many questions changed. */
    fun applyShared(docs: List<CloudDoc>): Int {
        var changed = 0
        for (doc in docs) {
            if (doc.deleted) {
                if (shared.remove(doc.id) != null) changed++
            } else {
                val q = decodeQuestion(doc.payload) ?: continue
                shared[doc.id] = q.copy(updatedAt = doc.updatedAt)
                changed++
            }
        }
        val newest = docs.maxOfOrNull { it.updatedAt } ?: meta.bankSyncedUpTo
        if (changed > 0) saveShared()
        if (newest > meta.bankSyncedUpTo) saveMeta(meta.copy(bankSyncedUpTo = newest))
        if (changed > 0) rebuild()
        return changed
    }

    /**
     * After an admin wrote [published] to the shared bank: caches them as shared and removes the
     * admin's own copies (synced as deletions), so each question exists once.
     */
    fun markPublished(published: List<Question>) {
        published.forEach { shared[it.id] = it }
        saveShared()
        val ownIds = published.map { it.id }.filter { id -> own.any { it.id == id } }.toSet()
        if (ownIds.isNotEmpty()) deleteOwn(ownIds) else rebuild()
    }

    /** After an admin deleted [ids] from the shared bank. */
    fun markUnpublished(ids: Set<String>) {
        ids.forEach { shared.remove(it) }
        saveShared()
        rebuild()
    }

    // ---- The running practice session (local only, never synced) ----

    /** The session that was running when the app last stopped, if any. */
    fun loadActiveSession(): SavedSession? = readDataFile(ACTIVE_SESSION_FILE)
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(SavedSession.serializer(), it) }.getOrNull() }

    /** Stores the running session, or clears it with null once the session is finished or abandoned. */
    fun saveActiveSession(session: SavedSession?) {
        writeDataFile(ACTIVE_SESSION_FILE, session?.let { json.encodeToString(SavedSession.serializer(), it) }.orEmpty())
    }

    // ---- Results ----

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

    // ---- Images ----

    /** Images referenced by the user's own questions or saved results (synced to their account). */
    fun referencedImages(): Set<String> =
        (ownQuestions().flatMap { it.imageRefs() } +
            results.flatMap { r -> r.answers.flatMap { a -> (listOf(a.questionText, a.explanation) + a.choices).flatMap(::imageRefs) } })
            .toSet() - sharedImageRefs()

    /** Images referenced by shared-bank questions (stored with the shared bank). */
    fun sharedImageRefs(): Set<String> = shared.values.flatMap { it.imageRefs() }.toSet()

    fun isImageUploaded(key: String, name: String): Boolean = name in meta.uploadedImages[key].orEmpty()

    fun markImageUploaded(key: String, name: String) {
        if (isImageUploaded(key, name)) return
        saveMeta(meta.copy(uploadedImages = meta.uploadedImages + (key to (meta.uploadedImages[key].orEmpty() + name))))
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

    /** All visible questions (shared bank and own) as importable JSON. */
    fun exportQuestionsJson(): String = json.encodeToString(ListSerializer(Question.serializer()), questions.toList())

    /**
     * Imports questions from a JSON array into the user's own questions. Each item needs `topic`,
     * `text`, `choices` and either `correctIndex` (0-based) or `answer` (a letter such as "C"). Items
     * whose `id` matches an own question replace it; items whose `id` is in the shared bank are
     * returned in [ImportSummary.shared] instead. A question already present under another id (see
     * [duplicateGroups]) is skipped, unless the new copy is the one to keep (e.g. a practice-test id
     * replacing a random-id copy), in which case the old own copy is deleted.
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
        val (forShared, forOwn) = imported.partition { it.id in shared }
        val byContent = questions.groupBy(::contentKey).mapValues { it.value.toMutableList() }.toMutableMap()
        val replaced = mutableSetOf<String>()
        var skipped = 0
        val accepted = forOwn.filter { q ->
            val copies = byContent.getOrPut(contentKey(q)) { mutableListOf() }
            val others = copies.filter { it.id != q.id }
            val accept = others.isEmpty() || keepOrder.compare(q, others.minWith(keepOrder)) < 0
            if (accept) {
                replaced += others.map { it.id }.filter { it !in shared }
                copies.clear()
                copies += q
            } else {
                skipped++
            }
            accept
        }
        accepted.forEach(::putOwn)
        saveOwn()
        if (replaced.isNotEmpty()) deleteOwn(replaced) else rebuild()
        if (accepted.isNotEmpty()) localChange()
        return ImportSummary(imported = accepted.size, skippedDuplicates = skipped, shared = forShared)
    }

    /**
     * Two-way merge of the user's own data with their cloud copy. Questions: the most recently
     * edited version wins, whether it's an edit or a deletion. Results never change after they're
     * saved, so they're merged as a union, and a deletion on either side wins. Remote winners are
     * applied locally right away; the returned plan lists the local winners that must be uploaded.
     */
    fun mergeRemote(remoteQuestions: List<CloudDoc>, remoteResults: List<CloudDoc>): SyncPlan {
        var downloaded = 0
        val uploads = mutableListOf<CloudWrite>()
        val deletedQuestions = meta.deletedQuestions.toMutableMap()
        val deletedResults = meta.deletedResults.toMutableMap()

        // ---- Questions ----
        val localQuestions = own.associateBy { it.id }
        val remoteQ = remoteQuestions.associateBy { it.id }
        for (id in localQuestions.keys + deletedQuestions.keys + remoteQ.keys) {
            val local = localQuestions[id]
            val localTime = local?.updatedAt ?: deletedQuestions[id]
            val remote = remoteQ[id]
            when {
                remote != null && (localTime == null || remote.updatedAt > localTime) -> {
                    if (remote.deleted) {
                        if (local != null) {
                            own.removeAll { it.id == id }
                            downloaded++
                        }
                        deletedQuestions[id] = remote.updatedAt
                    } else {
                        val q = decodeQuestion(remote.payload) ?: continue
                        putOwn(q.copy(updatedAt = remote.updatedAt))
                        deletedQuestions.remove(id)
                        downloaded++
                    }
                }
                localTime != null && (remote == null || remote.updatedAt < localTime) -> {
                    uploads += if (local != null) {
                        CloudWrite(QUESTIONS_COLLECTION, CloudDoc(id, encodeQuestion(local), local.updatedAt, false))
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
            saveOwn()
            saveResults()
            rebuild()
        }
        if (deletedQuestions != meta.deletedQuestions || deletedResults != meta.deletedResults) {
            saveMeta(meta.copy(deletedQuestions = deletedQuestions, deletedResults = deletedResults))
        }
        return SyncPlan(downloaded, uploads)
    }

    private fun putOwn(q: Question) {
        val index = own.indexOfFirst { it.id == q.id }
        if (index >= 0) own[index] = q else own.add(q)
        if (q.id in meta.deletedQuestions) saveMeta(meta.copy(deletedQuestions = meta.deletedQuestions - q.id))
    }

    private fun localChange() {
        version++
    }

    private fun saveOwn() {
        writeDataFile(QUESTIONS_FILE, json.encodeToString(ListSerializer(Question.serializer()), own.toList()))
    }

    private fun saveShared() {
        writeDataFile(BANK_FILE, json.encodeToString(ListSerializer(Question.serializer()), shared.values.toList()))
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
