package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.data.Repository
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.isPracticeTestQuestion
import kz.arctan.grepractice.practice.formatDuration
import kotlin.math.roundToInt

private enum class Timing(val label: String) { GrePace("GRE pace"), Custom("Custom limit"), Untimed("Untimed") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: AppViewModel, setup: Screen.Setup) {
    val repo = vm.repo
    val topics = repo.topics
    val exam = setup.exam

    var selectedTopics by remember {
        mutableStateOf(if (exam || setup.topic == null) topics.toSet() else setOf(setup.topic))
    }
    // Random exams leave the full practice tests' questions out by default, so the tests stay unseen.
    val hasPracticeTests = exam && repo.questions.any { isPracticeTestQuestion(it.id) }
    var includePracticeTests by remember { mutableStateOf(false) }
    val pool = repo.questions.filter {
        it.topic in selectedTopics && (!hasPracticeTests || includePracticeTests || !isPracticeTestQuestion(it.id))
    }
    var count by remember { mutableIntStateOf(if (exam) Gre.EXAM_QUESTIONS else 10) }
    val effectiveCount = count.coerceIn(0, pool.size)
    var timing by remember { mutableStateOf(Timing.GrePace) }
    var customMinutes by remember { mutableStateOf("30") }
    var instantFeedback by remember { mutableStateOf(!exam) }
    var shuffleChoices by remember { mutableStateOf(false) }
    var preferWeak by remember { mutableStateOf(!exam) }

    val timeLimitMs: Long? = when (timing) {
        Timing.GrePace -> effectiveCount * Gre.PACE_MS_PER_QUESTION
        Timing.Custom -> customMinutes.toIntOrNull()?.takeIf { it > 0 }?.let { it * 60_000L }
        Timing.Untimed -> null
    }
    val canStart = effectiveCount > 0 && (timing != Timing.Custom || timeLimitMs != null)

    ScreenScaffold(
        title = if (exam) "Random exam" else "Practice by topic",
        onBack = vm::back,
        bottomBar = {
            CenteredBar {
                Text(
                    buildString {
                        append("$effectiveCount questions · ")
                        append(timeLimitMs?.let { formatDuration(it) } ?: "untimed")
                    },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                )
                Button(
                    enabled = canStart,
                    onClick = {
                        val picked = pickQuestions(repo, pool, effectiveCount, preferWeak)
                        vm.startSession(
                            PracticeConfig(
                                mode = if (exam) PracticeMode.EXAM else PracticeMode.TOPIC,
                                title = if (exam) "Random exam" else topicTitle(selectedTopics, topics.size),
                                questions = picked,
                                timeLimitMs = timeLimitMs,
                                instantFeedback = instantFeedback,
                                shuffleChoices = shuffleChoices,
                            ),
                        )
                    },
                ) { Text("Start") }
            }
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (exam) {
                SectionCard {
                    Text(
                        "The real GRE Mathematics Subject Test has ${Gre.EXAM_QUESTIONS} questions in ${Gre.EXAM_MINUTES} minutes " +
                            "(about ${formatDuration(Gre.PACE_MS_PER_QUESTION)} per question): roughly 50% calculus, 25% algebra " +
                            "and 25% additional topics. Questions are drawn at random from your bank." +
                            if (pool.size < Gre.EXAM_QUESTIONS) {
                                " Only ${pool.size} questions are available, so the exam is shortened and the time limit scaled to match."
                            } else "",
                    )
                }
            }

            if (!exam) {
                SectionCard(title = "Topics") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedTopics = topics.toSet() }) { Text("Select all") }
                        TextButton(onClick = { selectedTopics = emptySet() }) { Text("Clear") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        topics.forEach { topic ->
                            val n = repo.questions.count { it.topic == topic }
                            SelectChip(
                                selected = topic in selectedTopics,
                                onClick = {
                                    selectedTopics = if (topic in selectedTopics) selectedTopics - topic else selectedTopics + topic
                                },
                                label = { Text("$topic ($n)") },
                            )
                        }
                    }
                    if (topics.isEmpty()) Text("Your question bank is empty. Add questions first.")
                }
            }

            SectionCard(title = "Number of questions") {
                if (pool.isEmpty()) {
                    Text("No questions match the selected topics.", color = MaterialTheme.colorScheme.error)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = effectiveCount.toFloat(),
                            onValueChange = { count = it.roundToInt() },
                            valueRange = 1f..pool.size.toFloat().coerceAtLeast(1f),
                            steps = (pool.size - 2).coerceAtLeast(0),
                            enabled = pool.size > 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$effectiveCount / ${pool.size}",
                            modifier = Modifier.width(80.dp).padding(start = 12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            SectionCard(title = "Timing") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Timing.entries.forEach { t ->
                        SelectChip(selected = timing == t, onClick = { timing = t }, label = { Text(t.label) })
                    }
                }
                when (timing) {
                    Timing.GrePace -> Text(
                        "${formatDuration(Gre.PACE_MS_PER_QUESTION)} per question → ${formatDuration(effectiveCount * Gre.PACE_MS_PER_QUESTION)} total. The session is submitted automatically when time runs out.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Timing.Custom -> OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("Total minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = timeLimitMs == null,
                    )
                    Timing.Untimed -> Text(
                        "A stopwatch still records time spent per question.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionCard(title = "Options") {
                OptionSwitch(
                    "Check answers as I go",
                    "Lock in each answer and see the solution immediately. Off = review everything at the end, like the real test.",
                    instantFeedback,
                ) { instantFeedback = it }
                OptionSwitch("Shuffle answer choices", "Reorder choices A–E for each question.", shuffleChoices) {
                    shuffleChoices = it
                }
                OptionSwitch(
                    "Prefer unseen and missed questions",
                    "Questions you've never answered, or got wrong last time, are picked first.",
                    preferWeak,
                ) { preferWeak = it }
                if (hasPracticeTests) {
                    OptionSwitch(
                        "Include practice-test questions",
                        "Also draw from the full practice tests. Leave off to keep those tests unseen for timed runs.",
                        includePracticeTests,
                    ) { includePracticeTests = it }
                }
            }
        }
    }
}

@Composable
private fun OptionSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun topicTitle(selected: Set<String>, totalTopics: Int): String = when {
    selected.size == totalTopics && totalTopics > 1 -> "All topics"
    selected.size <= 3 -> selected.sorted().joinToString(", ")
    else -> "${selected.size} topics"
}

/** Random sample of [count] questions; with [preferWeak], unseen and last-missed questions come first. */
fun pickQuestions(repo: Repository, pool: List<Question>, count: Int, preferWeak: Boolean): List<Question> {
    if (!preferWeak) return pool.shuffled().take(count)
    // Results are newest-first, so the first record seen for a question is its latest attempt.
    val lastCorrect = HashMap<String, Boolean>()
    repo.results.forEach { r -> r.answers.forEach { a -> lastCorrect.getOrPut(a.questionId) { a.isCorrect } } }
    val (weak, strong) = pool.partition { lastCorrect[it.id] != true }
    return (weak.shuffled() + strong.shuffled()).take(count).shuffled()
}
