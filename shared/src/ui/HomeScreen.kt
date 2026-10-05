package kz.arctan.grepractice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.practice.formatDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val repo = vm.repo
    ScreenScaffold(title = "GRE Math Subject Practice", onBack = null) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repo.loadError?.let { error ->
                SectionCard {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Text("A copy of the unreadable file was saved next to it with a .broken extension.")
                }
            }

            val allAnswers = repo.results.flatMap { it.answers }
            val accuracy = if (allAnswers.isEmpty()) "—" else "${allAnswers.count { it.isCorrect } * 100 / allAnswers.size}%"
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Questions in bank", repo.questions.size.toString(), Modifier.widthIn(min = 150.dp))
                StatTile("Practice sessions", repo.results.size.toString(), Modifier.widthIn(min = 150.dp))
                StatTile("Questions answered", allAnswers.size.toString(), Modifier.widthIn(min = 150.dp))
                StatTile("Overall accuracy", accuracy, Modifier.widthIn(min = 150.dp))
            }

            Text("Practice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    "Simulated exam",
                    "${Gre.EXAM_QUESTIONS} questions · ${Gre.EXAM_MINUTES / 60} h ${Gre.EXAM_MINUTES % 60} min, answers revealed at the end",
                ) { vm.navigate(Screen.Setup(exam = true)) }
                ActionCard("Practice by topic", "Pick topics, question count and timing") {
                    vm.navigate(Screen.Setup(exam = false))
                }
                ActionCard("Random question", "One question at a time with a stopwatch and instant check") {
                    vm.navigate(Screen.Random)
                }
            }

            Text("Manage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Question bank", "Browse, add, edit and delete questions") { vm.navigate(Screen.Bank) }
                ActionCard("History", "Review past sessions and your answers") { vm.navigate(Screen.History) }
                ActionCard("Import / export", "Bulk add questions as JSON or back up your bank") {
                    vm.navigate(Screen.Transfer)
                }
            }

            TopicPerformance(vm)
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(262.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun TopicPerformance(vm: AppViewModel) {
    val stats = vm.repo.topicStats()
    val fb = LocalFeedbackColors.current
    SectionCard(title = "Performance by topic") {
        if (stats.isEmpty()) {
            Text("Finish a practice session to see per-topic accuracy here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Text(
            "Accuracy across every saved answer. Average time is compared to GRE pace (${formatDuration(Gre.PACE_MS_PER_QUESTION)} per question). Tap a topic to practice it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        stats.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { vm.navigate(Screen.Setup(exam = false, topic = s.topic)) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.topic, fontWeight = FontWeight.Medium)
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            s.percent >= 75 -> fb.correct
                            s.percent >= 50 -> fb.flag
                            else -> fb.incorrect
                        },
                    )
                }
                Text("${s.correct}/${s.answered}", modifier = Modifier.width(56.dp))
                Text("${s.percent}%", fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                Text(
                    formatDuration(s.avgTimeMs),
                    color = if (s.avgTimeMs > Gre.PACE_MS_PER_QUESTION) fb.incorrect else Color.Unspecified,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        TextButton(onClick = { vm.navigate(Screen.History) }) { Text("See all sessions →") }
    }
}
