package kz.arctan.grepractice.practice

import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kotlin.random.Random

/** Where a picked question came from, judged by its latest attempt. */
enum class PickReason(val label: String) {
    MISSED("Missed last time"),
    SKIPPED("Left unanswered last time"),
    UNSEEN("Not attempted yet"),
    REVIEW("Review: answered correctly before"),
}

data class Pick(
    val question: Question,
    val reason: PickReason,
    /** The user's recent average time on this question's topic, when it is slower than GRE pace. */
    val slowTopicAvgMs: Long? = null,
)

/**
 * Chooses practice questions that need work, from the user's [results] (newest first).
 *
 * A question's latest attempt puts it in a bucket: missed, unanswered (left blank or never
 * attempted), or answered correctly. A bucket is chosen first by fixed shares, so a few missed
 * questions still come up often in a large, mostly unseen bank, and then a question within it.
 * Questions from topics where the user's recent answers take longer than GRE pace are weighted up
 * there, in proportion to how slow the topic is (capped at [MAX_TOPIC_WEIGHT] times).
 */
class QuestionPicker(results: List<PracticeResult>) {
    private val latest = HashMap<String, AnswerRecord>()
    private val topicAvgMs: Map<String, Long>

    init {
        val recentTimes = HashMap<String, MutableList<Long>>()
        for (r in results) {
            for (a in r.answers) {
                latest.getOrPut(a.questionId) { a }
                if (a.isAnswered && a.timeSpentMs > 0) {
                    val times = recentTimes.getOrPut(a.topic) { mutableListOf() }
                    if (times.size < TOPIC_SAMPLE) times += a.timeSpentMs
                }
            }
        }
        topicAvgMs = recentTimes.filterValues { it.size >= MIN_TOPIC_SAMPLE }.mapValues { (_, t) -> t.sum() / t.size }
    }

    fun reasonFor(question: Question): PickReason {
        val last = latest[question.id] ?: return PickReason.UNSEEN
        return when {
            !last.isAnswered -> PickReason.SKIPPED
            !last.isCorrect -> PickReason.MISSED
            else -> PickReason.REVIEW
        }
    }

    /** The user's recent average time on [topic], or null if it is within GRE pace or there are too few answers. */
    fun slowTopicAvgMs(topic: String): Long? = topicAvgMs[topic]?.takeIf { it > Gre.PACE_MS_PER_QUESTION }

    /** 1 for topics within pace, up to [MAX_TOPIC_WEIGHT] for topics that take that many times longer. */
    fun topicWeight(topic: String): Double =
        (slowTopicAvgMs(topic) ?: return 1.0).toDouble().div(Gre.PACE_MS_PER_QUESTION).coerceAtMost(MAX_TOPIC_WEIGHT)

    fun pick(pool: List<Question>, random: Random = Random.Default): Pick? {
        if (pool.isEmpty()) return null
        val buckets = pool.groupBy { bucketOf(reasonFor(it)) }
        val shares = Bucket.entries.filter { it in buckets }.associateWith { it.share }
        val bucket = weightedChoice(shares.keys.toList(), random) { shares.getValue(it) }
        val question = weightedChoice(buckets.getValue(bucket), random) { topicWeight(it.topic) }
        return Pick(question, reasonFor(question), slowTopicAvgMs(question.topic))
    }

    /**
     * [count] distinct questions from [pool] (all of it if smaller), each drawn as [pick] would from
     * the ones not chosen yet, so a session gets the same mix that Random question shows one by one.
     */
    fun pickMany(pool: List<Question>, count: Int, random: Random = Random.Default): List<Question> {
        val remaining = pool.toMutableList()
        return List(minOf(count, pool.size)) {
            val question = pick(remaining, random)!!.question
            remaining.removeAt(remaining.indexOfFirst { it === question })
            question
        }
    }

    private enum class Bucket(val share: Double) { MISSED(0.45), UNANSWERED(0.40), REVIEW(0.15) }

    private fun bucketOf(reason: PickReason) = when (reason) {
        PickReason.MISSED -> Bucket.MISSED
        PickReason.SKIPPED, PickReason.UNSEEN -> Bucket.UNANSWERED
        PickReason.REVIEW -> Bucket.REVIEW
    }

    private fun <T> weightedChoice(items: List<T>, random: Random, weight: (T) -> Double): T {
        val weights = items.map(weight)
        var x = random.nextDouble() * weights.sum()
        for (i in items.indices) {
            x -= weights[i]
            if (x < 0) return items[i]
        }
        return items.last()
    }

    companion object {
        /** Recent answers per topic used for its average time. */
        const val TOPIC_SAMPLE = 20
        const val MIN_TOPIC_SAMPLE = 3
        const val MAX_TOPIC_WEIGHT = 3.0
    }
}
