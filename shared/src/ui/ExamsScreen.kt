package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.data.formatDateTime
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.practice.PracticeTest
import kz.arctan.grepractice.practice.findPracticeTests
import kz.arctan.grepractice.practice.formatDuration

/** Entry point of "Simulated exam": the bank's full practice tests, plus a randomly assembled exam. */
@Composable
fun ExamsScreen(vm: AppViewModel) {
    val tests = findPracticeTests(vm.repo.questions)
    var confirmStart by remember { mutableStateOf<PracticeTest?>(null) }

    ScreenScaffold(title = "Simulated exam", onBack = vm::back) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "The real GRE Mathematics Subject Test has ${Gre.EXAM_QUESTIONS} questions in ${Gre.EXAM_MINUTES} minutes. " +
                    "A timed run reveals answers only when you finish; practicing untimed checks each answer as you go.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (tests.isEmpty()) {
                SectionCard(title = "Practice tests") {
                    Text(
                        "No practice tests in your bank. Questions with ids like \"practice1-01\" … \"practice1-66\" " +
                            "(test 1, questions 1–66) are grouped into a practice test automatically.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            tests.forEach { test ->
                PracticeTestCard(
                    vm = vm,
                    test = test,
                    onStartTimed = { confirmStart = test },
                    onStartUntimed = { vm.startSession(test.config(timed = false)) },
                )
            }

            SectionCard(title = "Random exam") {
                Text(
                    "A full-length exam drawn at random from your question bank" +
                        if (tests.isNotEmpty()) ", leaving out the practice tests' questions by default." else ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { vm.navigate(Screen.Setup(exam = true)) }) { Text("Set up random exam") }
            }
        }
    }

    confirmStart?.let { test ->
        ConfirmDialog(
            title = "Start ${test.title}?",
            text = "${test.questions.size} questions in ${formatDuration(test.timeLimitMs)}. The timer starts right away, " +
                "and the test is submitted automatically when time runs out.",
            confirmLabel = "Start",
            onConfirm = {
                confirmStart = null
                vm.startSession(test.config(timed = true))
            },
            onDismiss = { confirmStart = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeTestCard(vm: AppViewModel, test: PracticeTest, onStartTimed: () -> Unit, onStartUntimed: () -> Unit) {
    val attempts = vm.repo.results.filter { it.testId == test.id }
    val best = attempts.maxByOrNull { it.correct }
    val latest = attempts.maxByOrNull { it.startedAt }
    val fb = LocalFeedbackColors.current

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(test.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${test.questions.size} questions · ${formatDuration(test.timeLimitMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (best != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${best.correct}/${best.total}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (best.percent >= 70) fb.correct else if (best.percent < 40) fb.incorrect else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("best", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            when {
                latest == null -> "Not attempted yet"
                else -> "${attempts.size} attempt${if (attempts.size == 1) "" else "s"} · last ${formatDateTime(latest.startedAt)}: " +
                    "${latest.correct}/${latest.total}" + if (latest.timeLimitMs == null) " (untimed)" else ""
            },
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onStartTimed) { Text("Start timed exam") }
            OutlinedButton(onClick = onStartUntimed) { Text("Practice untimed") }
            if (latest != null) {
                Spacer(Modifier.padding(start = 4.dp))
                TextButton(onClick = { vm.navigate(Screen.Result(latest.id)) }) { Text("Review last attempt") }
            }
        }
    }
}
