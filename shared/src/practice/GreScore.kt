package kz.arctan.grepractice.practice

import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Scaled scores for the GRE Mathematics Test, from the conversion tables printed with the bank's
 * practice tests. Older books count right answers minus a quarter of the wrong ones; the current
 * official book (form GR3768) and today's exam count right answers only, so predictions use that
 * table.
 */
enum class ScoreScale {
    /** The scale in use since October 2001. */
    Current,
    /** The scale before the October 2001 rescaling; these scores run higher than today's. */
    Pre2001,
}

enum class ScoringRule(val description: String) {
    /** Right answers minus a quarter of the wrong ones, rounded; blanks count zero. */
    RightMinusQuarterWrong("right − ¼ × wrong, rounded"),
    /** The number of right answers; nothing is subtracted for wrong ones. */
    RightOnly("the number of right answers"),
}

/** Raw scores [rawMin]..[rawMax] earn [scaled]; [percentBelow] where the book prints it. */
data class ConversionRow(val rawMin: Int, val rawMax: Int, val scaled: Int, val percentBelow: Int?)

class ScoreConversion(
    /** The practice test this table belongs to, e.g. "practice1". */
    val testId: String,
    val form: String,
    val scale: ScoreScale,
    val rule: ScoringRule,
    /** Who the percentages were measured on, as the book states it, e.g. "test takers in 2004–07". */
    val percentBelowSource: String?,
    /** "rawMin-rawMax scaled [percent]" rows separated by commas, as printed. */
    table: String,
    /** False for a table that its author estimated rather than ETS published. */
    val official: Boolean = true,
) {
    val rows: List<ConversionRow> = table.split(',').map { row ->
        val parts = row.trim().split(' ')
        val raws = parts[0].split('-').map(String::toInt)
        ConversionRow(raws.first(), raws.last(), parts[1].toInt(), parts.getOrNull(2)?.toInt())
    }

    private fun row(raw: Int): ConversionRow = raw.coerceIn(0, Gre.EXAM_QUESTIONS).let { r -> rows.first { r in it.rawMin..it.rawMax } }

    fun scaled(raw: Int): Int = row(raw).scaled
    fun percentBelow(raw: Int): Int? = row(raw).percentBelow

    fun rawScore(correct: Int, incorrect: Int): Int = when (rule) {
        ScoringRule.RightMinusQuarterWrong -> formulaRawScore(correct, incorrect)
        ScoringRule.RightOnly -> correct
    }
}

object GreScores {
    val conversions: List<ScoreConversion> = listOf(
        ScoreConversion(
            "practice1", "GR1268", ScoreScale.Current, ScoringRule.RightMinusQuarterWrong, null,
            "63-66 910, 60-62 900, 58-59 890, 56-57 880, 54-55 870, 53 860, 52 850, 51 840, 50 830, 49 820, 48 810, " +
                "47 800, 46 790, 45 780, 44 770, 43 760, 42 750, 41 740, 40 730, 39 720, 38 710, 37 700, 36 690, 35 680, " +
                "34 670, 32-33 660, 31 650, 30 640, 29 630, 28 620, 27 610, 26 600, 25 590, 24 580, 23 570, 21-22 560, " +
                "20 550, 19 540, 18 530, 17 520, 16 510, 14-15 500, 13 490, 12 480, 11 470, 10 460, 8-9 450, 7 440, " +
                "6 430, 4-5 420, 3 410, 2 400, 0-1 390",
        ),
        ScoreConversion(
            "practice2", "GR0568", ScoreScale.Current, ScoringRule.RightMinusQuarterWrong, "test takers in 2004–07",
            "65-66 900 99, 64 890 98, 62-63 880 97, 61 870 96, 59-60 860 95, 58 850 94, 56-57 840 92, 55 830 91, " +
                "53-54 820 89, 52 810 88, 51 800 86, 49-50 790 84, 48 780 83, 46-47 770 81, 45 760 79, 44 750 77, " +
                "42-43 740 75, 41 730 72, 40 720 71, 38-39 710 68, 37 700 66, 36 690 64, 35 680 61, 33-34 670 59, " +
                "32 660 57, 31 650 54, 30 640 52, 28-29 630 48, 27 620 46, 26 610 44, 25 600 41, 23-24 590 38, " +
                "22 580 36, 21 570 33, 20 560 30, 19 550 28, 18 540 25, 16-17 530 22, 15 520 19, 14 510 17, 13 500 15, " +
                "12 490 13, 11 480 12, 10 470 10, 8-9 460 8, 7 450 6, 6 440 5, 5 430 4, 4 420 4, 3 410 3, 2 400 2, 0-1 390 1",
        ),
        ScoreConversion(
            "practice3", "GR9367", ScoreScale.Pre2001, ScoringRule.RightMinusQuarterWrong, "test takers in 1996–99",
            "55-66 990 82, 54 980 81, 53 970 80, 52 960 79, 51 950 77, 50 930 74, 49 920 72, 48 910 71, 47 900 69, " +
                "46 890 67, 45 880 66, 44 870 64, 43 860 62, 42 850 61, 41 840 59, 40 830 57, 39 820 55, 38 810 54, " +
                "37 800 52, 36 790 51, 35 780 49, 34 770 47, 33 760 45, 32 750 43, 31 740 41, 30 730 39, 29 720 38, " +
                "28 710 37, 27 700 35, 26 690 33, 25 680 31, 24 670 30, 23 660 28, 22 650 26, 21 640 24, 20 630 23, " +
                "19 620 22, 18 600 18, 17 590 17, 16 580 15, 15 570 13, 14 560 12, 13 550 11, 12 540 10, 11 530 8, " +
                "10 520 7, 9 510 6, 8 500 5, 7 490 4, 6 480 2, 5 470 2, 4 460 1, 3 450 1, 2 440 0, 1 430 0, 0 420 0",
        ),
        ScoreConversion(
            "practice4", "GR8767", ScoreScale.Pre2001, ScoringRule.RightMinusQuarterWrong, "test takers in 1983–86",
            "60-66 990 95, 59 980 94, 58 970 93, 57 960 92, 56 950 91, 55 940 90, 54 930 88, 53 920 87, 52 910 86, " +
                "51 900 85, 50 890 83, 49 880 81, 48 870 80, 47 860 78, 46 850 77, 44-45 840 75, 43 830 73, 42 820 71, " +
                "41 810 69, 40 800 67, 39 790 65, 38 780 63, 37 770 61, 36 760 59, 35 750 57, 34 740 55, 33 730 53, " +
                "32 720 51, 31 710 48, 30 700 46, 29 690 44, 28 680 42, 27 670 40, 26 660 38, 25 650 35, 24 640 33, " +
                "23 630 31, 22 620 29, 21 610 27, 20 600 25, 19 590 23, 17-18 580 21, 16 570 19, 15 560 18, 14 550 16, " +
                "13 540 15, 12 530 14, 11 520 12, 10 510 11, 9 500 10, 8 490 8, 7 480 7, 6 470 6, 5 460 5, 4 450 4, " +
                "3 440 3, 2 430 3, 1 420 2, 0 410 2",
        ),
        ScoreConversion(
            "practice5", "GR9768", ScoreScale.Current, ScoringRule.RightMinusQuarterWrong, "test takers in 1997–2000",
            "66 890 99, 65 880 98, 64 870 97, 62-63 860 97, 61 850 95, 60 840 94, 59 830 93, 57-58 820 92, 56 810 90, " +
                "55 800 88, 54 790 87, 52-53 780 85, 51 770 83, 50 760 82, 49 750 80, 47-48 740 78, 46 730 76, 45 720 74, " +
                "44 710 72, 43 700 70, 41-42 690 68, 40 680 66, 39 670 63, 38 660 61, 36-37 650 58, 35 640 56, 34 630 53, " +
                "33 620 51, 31-32 610 48, 30 600 46, 29 590 43, 28 580 41, 26-27 570 37, 25 560 35, 24 550 33, 23 540 31, " +
                "21-22 530 29, 20 520 27, 19 510 24, 18 500 22, 17 490 20, 15-16 480 17, 14 470 15, 13 460 13, 12 450 11, " +
                "10-11 440 9, 9 430 8, 8 420 6, 7 410 5, 5-6 400 3, 4 390 2, 3 380 1, 2 370 1, 0-1 360 1",
        ),
        ScoreConversion(
            "practice6", "GR3768", ScoreScale.Current, ScoringRule.RightOnly, null,
            "65-66 970, 64 960, 63 920, 62 900, 61 890, 60 880, 59 870, 57-58 860, 56 850, 55 840, 53-54 830, 52 820, " +
                "51 810, 50 800, 49 790, 48 770, 47 760, 46 750, 45 740, 44 720, 43 710, 42 700, 41 680, 40 670, 39 660, " +
                "38 650, 37 630, 36 620, 35 610, 34 600, 33 580, 32 570, 31 560, 30 550, 29 540, 28 530, 27 520, 26 510, " +
                "25 500, 24 490, 23 480, 22 470, 21 460, 20 450, 19 440, 18 430, 17 420, 16 410, 15 400, 14 390, 13 370, " +
                "12 360, 11 350, 10 330, 9 320, 8 300, 7 280, 6 260, 5 230, 0-4 200",
        ),
        ScoreConversion(
            "practice7", "MSDC01", ScoreScale.Current, ScoringRule.RightOnly, "the test author's estimates",
            "66 970 99, 65 960 99, 64 950 99, 63 940 99, 62 930 99, 61 920 98, 60 910 96, 59 900 94, 58 890 92, " +
                "57 880 90, 55-56 870 89, 54 860 87, 53 850 86, 52 840 84, 51 830 83, 50 820 81, 49 810 80, 48 800 78, " +
                "47 790 77, 46 780 75, 44-45 770 74, 43 760 71, 42 750 70, 41 740 67, 40 730 66, 39 720 63, 38 710 61, " +
                "37 700 59, 36 690 57, 35 680 55, 33-34 670 53, 32 660 50, 31 650 48, 30 640 46, 29 630 44, 28 620 41, " +
                "27 610 39, 26 600 37, 25 590 34, 24 580 31, 22-23 570 29, 21 560 27, 20 550 25, 19 540 22, 18 530 20, " +
                "17 520 18, 16 510 16, 15 500 14, 14 490 12, 13 480 11, 11-12 470 9, 10 460 8, 9 450 6, 8 440 5, " +
                "7 430 4, 6 420 3, 5 410 2, 4 400 2, 3 390 1, 2 380 1, 1 370 0, 0 360 0",
            official = false,
        ),
    )

    fun conversionFor(testId: String?): ScoreConversion? = conversions.firstOrNull { it.testId == testId }

    /** The table predictions use: the current official practice test, scored like today's exam (right answers only). */
    val reference: ScoreConversion = conversionFor("practice6")!!

    /** The scaled score for [rightAnswers] on today's exam, by [reference]. */
    fun referenceScaled(rightAnswers: Int): Int = reference.scaled(rightAnswers)

    /** The number of right answers whose scaled score is closest to [scaled] (the lowest such on ties). */
    fun referenceRaw(scaled: Int): Int = (0..Gre.EXAM_QUESTIONS).minBy { abs(reference.scaled(it) - scaled) }
}

/** Right answers minus a quarter of the wrong ones, rounded to the nearest whole number, as in the books. */
fun formulaRawScore(correct: Int, incorrect: Int): Int = floor(correct - incorrect / 4.0 + 0.5).toInt().coerceAtLeast(0)

/** A practice test scored with its own book's table. */
data class PracticeTestScore(
    val conversion: ScoreConversion,
    val correct: Int,
    val incorrect: Int,
    val raw: Int,
    val scaled: Int,
    val percentBelow: Int?,
    /**
     * The score on today's scale: [scaled] for current-scale forms, and for pre-2001 forms what the
     * same right answers earn by [GreScores.reference].
     */
    val currentScaleEquivalent: Int,
)

/** The official-style score of [result] if it ran one of the bank's practice tests. */
fun practiceTestScore(result: PracticeResult): PracticeTestScore? {
    val conversion = GreScores.conversionFor(result.testId) ?: return null
    val correct = result.correct
    val incorrect = result.answers.count { it.isAnswered && !it.isCorrect }
    val raw = conversion.rawScore(correct, incorrect)
    val currentEquivalent = if (conversion.scale == ScoreScale.Current) conversion.scaled(raw) else GreScores.referenceScaled(correct)
    return PracticeTestScore(conversion, correct, incorrect, raw, conversion.scaled(raw), conversion.percentBelow(raw), currentEquivalent)
}

/** Fewer answers than this give too rough a prediction to show. */
const val MIN_ANSWERS_FOR_PREDICTION = 10

/**
 * The score of a full exam with the same share of right answers as [correct] out of [shown],
 * by [GreScores.reference]; null below [MIN_ANSWERS_FOR_PREDICTION].
 */
fun predictedScaled(correct: Int, shown: Int): Int? {
    if (shown < MIN_ANSWERS_FOR_PREDICTION) return null
    return GreScores.referenceScaled((correct.toDouble() / shown * Gre.EXAM_QUESTIONS).roundToInt())
}

/** A prediction for a session of random questions (not a practice test), or null. */
fun predictedSessionScore(result: PracticeResult): Int? =
    if (GreScores.conversionFor(result.testId) != null) null
    else predictedScaled(result.correct, result.total)

data class ExamPrediction(
    /** The prediction for exam day, after [STRESS_MARGIN]. */
    val score: Int,
    /** The same prediction without the stress margin. */
    val beforeStress: Int,
    /** Recency-weighted practice-test score on today's scale, if any test was taken. */
    val practiceTestScore: Int?,
    val practiceTests: Int,
    /** The score expected from accuracy per topic, weighted like the exam. */
    val topicScore: Int,
    val answered: Int,
    /** Topics that carry real weight on the exam where the user does worst, weakest first. */
    val weakTopics: List<String>,
    /** Share of the exam's questions in topics the user hasn't answered any questions in. */
    val unpracticedShare: Double,
)

/** Exam-day pressure: the prediction assumes this share of the raw score is lost. */
const val STRESS_MARGIN = 0.08
/** Weight of practice tests against per-topic accuracy when both exist. */
private const val PRACTICE_TEST_WEIGHT = 0.6
/** Answers a topic needs before its own accuracy outweighs the overall one. */
private const val TOPIC_PRIOR_ANSWERS = 5.0
/** Topics below this share of the exam aren't named as weak spots. */
private const val WEAK_TOPIC_MIN_SHARE = 0.03
/** Answers needed for a prediction when no practice test was taken. */
const val MIN_ANSWERS_FOR_EXAM_PREDICTION = 20

/**
 * Predicts the exam score from practice tests and per-topic accuracy, then lowers it by
 * [STRESS_MARGIN] for exam-day stress. Topics are weighted by how often they appear in the
 * bank's practice tests (a stand-in for the real exam's mix, or the whole [bank] without them).
 * Returns null until there is a practice test or [MIN_ANSWERS_FOR_EXAM_PREDICTION] answers.
 */
fun predictExamScore(results: List<PracticeResult>, bank: List<Question>): ExamPrediction? {
    val answers = results.flatMap { it.answers }
    val tests = results.mapNotNull(::practiceTestScore)
    if (tests.isEmpty() && answers.size < MIN_ANSWERS_FOR_EXAM_PREDICTION) return null

    // Right answers, overall and per topic, as today's exam counts them.
    fun score(list: List<AnswerRecord>) = list.count { it.isCorrect }.toDouble()
    val overall = if (answers.isEmpty()) 0.0 else score(answers) / answers.size
    val byTopic = answers.groupBy { it.topic }
    fun topicRate(topic: String): Double {
        val list = byTopic[topic].orEmpty()
        return (score(list) + TOPIC_PRIOR_ANSWERS * overall) / (list.size + TOPIC_PRIOR_ANSWERS)
    }
    val examQuestions = bank.filter { isPracticeTestQuestion(it.id) }.ifEmpty { bank }
    val weights = examQuestions.groupingBy { it.topic }.eachCount().mapValues { (_, n) -> n.toDouble() / examQuestions.size }
    val topicRaw = if (weights.isEmpty()) overall * Gre.EXAM_QUESTIONS
    else weights.entries.sumOf { (topic, w) -> w * topicRate(topic) } * Gre.EXAM_QUESTIONS
    val topicScore = GreScores.referenceScaled(topicRaw.roundToInt())

    // Practice tests on today's scale, the latest counting most (weights 3, 2, 1).
    val recent = results.mapNotNull { r -> practiceTestScore(r)?.let { r.startedAt to it.currentScaleEquivalent } }
        .sortedByDescending { it.first }.take(3)
    val practiceScore = if (recent.isEmpty()) null
    else (recent.mapIndexed { i, (_, s) -> (3 - i) * s }.sum().toDouble() / recent.indices.sumOf { 3 - it }).roundToInt()

    val combined = practiceScore?.let { PRACTICE_TEST_WEIGHT * it + (1 - PRACTICE_TEST_WEIGHT) * topicScore } ?: topicScore.toDouble()
    val beforeStress = (combined / 10).roundToInt() * 10
    val stressedRaw = (GreScores.referenceRaw(beforeStress) * (1 - STRESS_MARGIN)).roundToInt()
    val score = minOf(beforeStress, GreScores.referenceScaled(stressedRaw))

    val weakTopics = weights.filterValues { it >= WEAK_TOPIC_MIN_SHARE }.keys
        .filter { byTopic.containsKey(it) }
        .sortedBy(::topicRate)
        .take(3)
        .filter { topicRate(it) < overall }
    return ExamPrediction(
        score = score,
        beforeStress = beforeStress,
        practiceTestScore = practiceScore?.let { (it / 10.0).roundToInt() * 10 },
        practiceTests = tests.size,
        topicScore = topicScore,
        answered = answers.size,
        weakTopics = weakTopics,
        unpracticedShare = weights.filterKeys { it !in byTopic }.values.sum(),
    )
}
