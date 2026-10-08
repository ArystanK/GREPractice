package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.data.formatDateTime
import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.formatDuration

private enum class ReviewFilter(val label: String) { All("All"), Incorrect("Incorrect"), Unanswered("Unanswered"), Flagged("Flagged") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(vm: AppViewModel, resultId: String) {
    val result = vm.repo.results.firstOrNull { it.id == resultId }
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.All) }

    ScreenScaffold(
        title = "Results",
        onBack = vm::back,
        actions = { if (vm.backStack.size > 2) androidx.compose.material3.TextButton(onClick = { vm.goHome() }) { Text("Home") } },
    ) { padding ->
        if (result == null) {
            EmptyState("This result was deleted.")
            return@ScreenScaffold
        }
        val shown = result.answers.withIndex().filter { (_, a) ->
            when (filter) {
                ReviewFilter.All -> true
                ReviewFilter.Incorrect -> a.isAnswered && !a.isCorrect
                ReviewFilter.Unanswered -> !a.isAnswered
                ReviewFilter.Flagged -> a.flagged
            }
        }
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScoreSummary(result) }
            item { TopicBreakdown(result) }
            item { RetryButtons(vm, result) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewFilter.entries.forEach { f ->
                        val n = when (f) {
                            ReviewFilter.All -> result.total
                            ReviewFilter.Incorrect -> result.answers.count { it.isAnswered && !it.isCorrect }
                            ReviewFilter.Unanswered -> result.answers.count { !it.isAnswered }
                            ReviewFilter.Flagged -> result.answers.count { it.flagged }
                        }
                        SelectChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} ($n)") })
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState("Nothing here.") }
            // Show questions as they are now, so fixes made since the session (e.g. restored math) appear.
            val current = vm.repo.questions.associateBy { it.id }
            itemsIndexed(shown, key = { _, v -> v.index }) { _, (index, answer) ->
                AnswerReview(index, answer.refreshedFrom(current[answer.questionId]))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreSummary(result: PracticeResult) {
    val fb = LocalFeedbackColors.current
    SectionCard {
        Text(result.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${result.mode.label} · ${formatDateTime(result.startedAt)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (result.timedOut) Text("Time ran out — the session was submitted automatically.", color = fb.incorrect)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Score",
                "${result.correct} / ${result.total}",
                Modifier.widthIn(min = 120.dp),
                valueColor = if (result.percent >= 70) fb.correct else if (result.percent < 40) fb.incorrect else androidx.compose.ui.graphics.Color.Unspecified,
            )
            StatTile("Accuracy", "${result.percent}%", Modifier.widthIn(min = 120.dp))
            StatTile(
                "Time used",
                formatDuration(result.durationMs) + (result.timeLimitMs?.let { " / ${formatDuration(it)}" } ?: ""),
                Modifier.widthIn(min = 120.dp),
            )
            if (result.total > 0) {
                val avg = result.durationMs / result.total
                StatTile(
                    "Avg per question",
                    formatDuration(avg),
                    Modifier.widthIn(min = 120.dp),
                    valueColor = if (avg > Gre.PACE_MS_PER_QUESTION) fb.incorrect else androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
        }
        val unanswered = result.answers.count { !it.isAnswered }
        Text(
            "${result.correct} correct · ${result.total - result.correct - unanswered} incorrect · $unanswered unanswered",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TopicBreakdown(result: PracticeResult) {
    val byTopic = result.answers.groupBy { it.topic }
    if (byTopic.size < 2) return
    SectionCard(title = "By topic") {
        byTopic.entries.sortedBy { it.key }.forEachIndexed { i, (topic, answers) ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(topic, modifier = Modifier.weight(1f))
                Text("${answers.count { it.isCorrect }} / ${answers.size}", modifier = Modifier.width(64.dp))
                Text(formatDuration(answers.sumOf { it.timeSpentMs } / answers.size) + " avg", modifier = Modifier.width(80.dp))
            }
        }
    }
}

@Composable
private fun RetryButtons(vm: AppViewModel, result: PracticeResult) {
    val missedIds = result.answers.filter { !it.isCorrect }.map { it.questionId }.toSet()
    val missed = vm.repo.questions.filter { it.id in missedIds }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (missed.isNotEmpty()) {
            Button(onClick = {
                vm.startSession(
                    PracticeConfig(
                        mode = PracticeMode.RETRY,
                        title = "Retry: ${result.title}",
                        questions = missed.shuffled(),
                        timeLimitMs = missed.size * Gre.PACE_MS_PER_QUESTION,
                        instantFeedback = true,
                        shuffleChoices = true,
                    ),
                )
            }) { Text("Retry ${missed.size} missed") }
        }
        OutlinedButton(onClick = { vm.goHome() }) { Text("Back to home") }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AnswerReview(index: Int, answer: AnswerRecord) {
    val fb = LocalFeedbackColors.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    answer.isCorrect -> "✓"
                    answer.isAnswered -> "✗"
                    else -> "–"
                },
                color = when {
                    answer.isCorrect -> fb.correct
                    answer.isAnswered -> fb.incorrect
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Question ${index + 1}", fontWeight = FontWeight.SemiBold)
            TopicPill(answer.topic)
            if (answer.flagged) Text("⚑", color = fb.flag)
            Spacer(Modifier.weight(1f))
            Text("⏱ ${formatDuration(answer.timeSpentMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MissingFigureNote(answer.questionText, answer.choices)
        QuestionText(answer.questionText)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            answer.choices.forEachIndexed { c, text ->
                ChoiceRow(c, text, choiceState(c, answer.selectedIndex, answer.correctIndex, revealed = true), onClick = null)
            }
        }
        ExplanationBox(answer.explanation, if (answer.isAnswered) answer.isCorrect else null)
    }
}
