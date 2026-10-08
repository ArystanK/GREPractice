package kz.arctan.grepractice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.data.formatDateTime
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.TopicAttempt
import kz.arctan.grepractice.practice.TopicSessionScore
import kz.arctan.grepractice.practice.formatDuration
import kz.arctan.grepractice.practice.topicAttempts
import kz.arctan.grepractice.practice.topicMissedIds
import kz.arctan.grepractice.practice.topicSessionScores
import kz.arctan.grepractice.practice.topicSummaries
import kz.arctan.grepractice.ui.math.MathText

/** Sessions shown in the trend chart, newest last. */
private const val MAX_BARS = 30
/** With fewer sessions, bars keep the width they'd have with this many, instead of filling the chart. */
private const val MIN_BAR_SLOTS = 12

private enum class AttemptFilter(val label: String) { All("All"), Incorrect("Incorrect"), Unanswered("Unanswered"), Correct("Correct") }

/** Every answer given to questions of [topic], with its trend and shortcuts to practice it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicHistoryScreen(vm: AppViewModel, topic: String) {
    val repo = vm.repo
    val fb = LocalFeedbackColors.current
    var filter by rememberSaveable { mutableStateOf(AttemptFilter.All) }

    val attempts = topicAttempts(repo.results, topic)
    val summary = topicSummaries(repo.results).firstOrNull { it.topic == topic }
    val scores = topicSessionScores(repo.results, topic)
    val current = repo.questions.associateBy { it.id }
    val inBank = repo.questions.count { it.topic == topic }
    val missedIds = topicMissedIds(repo.results, topic)
    val missed = repo.questions.filter { it.id in missedIds }
    val shown = attempts.filter { a ->
        when (filter) {
            AttemptFilter.All -> true
            AttemptFilter.Incorrect -> a.answer.isAnswered && !a.answer.isCorrect
            AttemptFilter.Unanswered -> !a.answer.isAnswered
            AttemptFilter.Correct -> a.answer.isCorrect
        }
    }

    ScreenScaffold(title = topic, onBack = vm::back) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (summary == null) {
                item { EmptyState("No answers in $topic yet.") }
                item { Button(onClick = { vm.navigate(Screen.Setup(exam = false, topic = topic)) }) { Text("Practice $topic") } }
                return@LazyColumn
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val tile = Modifier.widthIn(min = 150.dp)
                    StatTile("Accuracy · ${summary.correct}/${summary.attempts}", "${summary.percent}%", tile, accuracyColor(summary.percent))
                    StatTile(
                        "Avg time · pace ${formatDuration(Gre.PACE_MS_PER_QUESTION)}",
                        summary.avgTimeMs?.let { formatDuration(it) } ?: "—",
                        tile,
                        if ((summary.avgTimeMs ?: 0) > Gre.PACE_MS_PER_QUESTION) fb.incorrect else Color.Unspecified,
                    )
                    StatTile("Questions seen", if (inBank > 0) "${summary.distinctQuestions} of $inBank" else "${summary.distinctQuestions}", tile)
                    StatTile("Last practiced", formatDate(summary.lastPracticedAt), tile)
                }
            }
            item {
                SectionCard(title = "Accuracy by session") {
                    SessionBars(scores.takeLast(MAX_BARS))
                    Text(
                        if (scores.size > MAX_BARS) "Your last $MAX_BARS sessions with $topic, oldest first." else "Each session with $topic, oldest first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.navigate(Screen.Setup(exam = false, topic = topic)) }) { Text("Practice $topic") }
                    if (missed.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            vm.startSession(
                                PracticeConfig(
                                    mode = PracticeMode.RETRY,
                                    title = "Retry: $topic",
                                    questions = missed.shuffled(),
                                    timeLimitMs = missed.size * Gre.PACE_MS_PER_QUESTION,
                                    instantFeedback = true,
                                    shuffleChoices = true,
                                ),
                            )
                        }) { Text("Retry ${missed.size} missed") }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttemptFilter.entries.forEach { f ->
                        val n = when (f) {
                            AttemptFilter.All -> attempts.size
                            AttemptFilter.Incorrect -> attempts.count { it.answer.isAnswered && !it.answer.isCorrect }
                            AttemptFilter.Unanswered -> attempts.count { !it.answer.isAnswered }
                            AttemptFilter.Correct -> attempts.count { it.answer.isCorrect }
                        }
                        SelectChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} ($n)") })
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState("Nothing here.") }
            // Show questions as they are now, so fixes made since (e.g. restored math) appear.
            itemsIndexed(shown, key = { _, a -> a.resultId + "/" + a.answer.questionId }) { _, a ->
                AttemptRow(a.copy(answer = a.answer.refreshedFrom(current[a.answer.questionId]))) {
                    vm.navigate(Screen.Result(a.resultId))
                }
            }
        }
    }
}

@Composable
private fun SessionBars(scores: List<TopicSessionScore>) {
    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        scores.forEach { s ->
            // A sliver for 0%, so the session still shows.
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight(maxOf(s.percent, 3) / 100f)
                    .background(accuracyColor(s.percent), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
        repeat(MIN_BAR_SLOTS - scores.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun AttemptRow(attempt: TopicAttempt, onOpen: () -> Unit) {
    val fb = LocalFeedbackColors.current
    val a = attempt.answer
    val (mark, markColor) = when {
        a.isCorrect -> "✓" to fb.correct
        a.isAnswered -> "✗" to fb.incorrect
        else -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(mark, color = markColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MathText(a.questionText, maxLines = 3, imageMaxHeight = 120.dp)
                Text(
                    buildString {
                        append(a.selectedIndex?.let { "Your answer ${Gre.letter(it)}" } ?: "Not answered")
                        if (!a.isCorrect) append(" · correct ${Gre.letter(a.correctIndex)}")
                        if (a.timeSpentMs > 0) append(" · ${formatDuration(a.timeSpentMs)}")
                        append(" · ${attempt.sessionTitle}, ${formatDateTime(attempt.at)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
