package kz.arctan.grepractice.practice

import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.PracticeResult

/** One answer to a question of a topic, with the session it was given in. */
data class TopicAttempt(val resultId: String, val at: Long, val sessionTitle: String, val answer: AnswerRecord)

/** How one session went on a single topic (a session may cover several topics). */
data class TopicSessionScore(val resultId: String, val at: Long, val correct: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else (correct * 100 + total / 2) / total
}

data class TopicSummary(
    val topic: String,
    /** Questions shown, including ones left blank, which count as not correct. */
    val attempts: Int,
    val correct: Int,
    /** Average over answered questions only, so skipped ones don't pull it down; null if none. */
    val avgTimeMs: Long?,
    val lastPracticedAt: Long,
    val distinctQuestions: Int,
) {
    val percent: Int get() = if (attempts == 0) 0 else (correct * 100 + attempts / 2) / attempts
}

/** Every attempt at [topic], newest first ([results] are newest first). */
fun topicAttempts(results: List<PracticeResult>, topic: String): List<TopicAttempt> =
    results.flatMap { r -> r.answers.filter { it.topic == topic }.map { TopicAttempt(r.id, r.startedAt, r.title, it) } }

/** The score on [topic] in each session that included it, oldest first, for a trend chart. */
fun topicSessionScores(results: List<PracticeResult>, topic: String): List<TopicSessionScore> =
    results.mapNotNull { r ->
        val answers = r.answers.filter { it.topic == topic }
        if (answers.isEmpty()) null else TopicSessionScore(r.id, r.startedAt, answers.count { it.isCorrect }, answers.size)
    }.sortedBy { it.at }

/** A summary per topic the user has practiced, weakest accuracy first. */
fun topicSummaries(results: List<PracticeResult>): List<TopicSummary> =
    results.flatMap { r -> r.answers.map { r.startedAt to it } }
        .groupBy { it.second.topic }
        .map { (topic, entries) ->
            val answers = entries.map { it.second }
            val timed = answers.filter { it.isAnswered && it.timeSpentMs > 0 }
            TopicSummary(
                topic = topic,
                attempts = answers.size,
                correct = answers.count { it.isCorrect },
                avgTimeMs = if (timed.isEmpty()) null else timed.sumOf { it.timeSpentMs } / timed.size,
                lastPracticedAt = entries.maxOf { it.first },
                distinctQuestions = answers.map { it.questionId }.toSet().size,
            )
        }
        .sortedWith(compareBy({ it.percent }, { it.topic }))

/** Ids of questions in [topic] whose latest attempt was not correct (missed or left blank). */
fun topicMissedIds(results: List<PracticeResult>, topic: String): Set<String> {
    val latest = LinkedHashMap<String, AnswerRecord>()
    topicAttempts(results, topic).forEach { latest.putIfAbsent(it.answer.questionId, it.answer) }
    return latest.filterValues { !it.isCorrect }.keys
}
