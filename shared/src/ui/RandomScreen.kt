package kz.arctan.grepractice.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.data.newId
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.practice.formatDuration

/** Recently shown question ids are skipped so the same question doesn't come up twice in a row. */
private const val RECENT_WINDOW = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RandomScreen(vm: AppViewModel) {
    val repo = vm.repo
    val fb = LocalFeedbackColors.current
    var topic by remember { mutableStateOf<String?>(null) }
    val pool = repo.questions.filter { topic == null || it.topic == topic }

    val recent = remember { ArrayDeque<String>() }
    fun draw(): Question? {
        if (pool.isEmpty()) return null
        val avoid = recent.takeLast(minOf(RECENT_WINDOW, pool.size - 1)).toSet()
        return pool.filter { it.id !in avoid }.random().also { recent.addLast(it.id) }
    }

    var question by remember { mutableStateOf<Question?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var checked by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(nowMillis()) }
    var now by remember { mutableLongStateOf(nowMillis()) }
    var stoppedMs by remember { mutableLongStateOf(0L) }
    var attempted by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }

    fun next() {
        question = draw()
        selected = null
        checked = false
        shownAt = nowMillis()
        now = shownAt
    }

    fun check() {
        val q = question ?: return
        val choice = selected ?: return
        if (checked) return
        checked = true
        stoppedMs = nowMillis() - shownAt
        attempted++
        if (choice == q.correctIndex) correct++
        repo.addResult(
            PracticeResult(
                id = newId(),
                mode = PracticeMode.RANDOM,
                title = "Random: ${q.topic}",
                startedAt = shownAt,
                durationMs = stoppedMs,
                answers = listOf(
                    AnswerRecord(
                        questionId = q.id,
                        topic = q.topic,
                        questionText = q.text,
                        choices = q.choices,
                        correctIndex = q.correctIndex,
                        selectedIndex = choice,
                        timeSpentMs = stoppedMs,
                        explanation = q.explanation,
                    ),
                ),
            ),
        )
    }

    LaunchedEffect(topic) { next() }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMillis()
            delay(250)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val elapsed = if (checked) stoppedMs else now - shownAt

    // Keyboard shortcuts; the focus target wraps the whole screen so it is attached before requestFocus runs.
    val onKey: (KeyEvent) -> Boolean = handler@{ e ->
        if (e.type != KeyEventType.KeyDown) return@handler false
        val q = question ?: return@handler false
        val choice = choiceForKey(e.key)
        when {
            choice != null && !checked && choice < q.choices.size -> selected = choice
            e.key == Key.Enter && !checked -> check()
            e.key == Key.Enter -> next()
            else -> return@handler false
        }
        true
    }

    Box(Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent(onKey)) {
        ScreenScaffold(
            title = "Random question",
            onBack = vm::back,
            actions = {
                Text(
                    formatDuration(elapsed),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (elapsed > Gre.PACE_MS_PER_QUESTION) fb.incorrect else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp),
                )
            },
            bottomBar = {
                CenteredBar {
                    Text(
                        if (attempted == 0) "This sitting: no answers yet" else "This sitting: $correct / $attempted correct",
                        modifier = Modifier.weight(1f),
                    )
                    if (!checked) {
                        OutlinedButton(onClick = ::next, enabled = pool.size > 1) { Text("Skip") }
                        Button(onClick = ::check, enabled = selected != null) { Text("Check") }
                    } else {
                        Button(onClick = ::next) { Text("Next question →") }
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectChip(selected = topic == null, onClick = { topic = null }, label = { Text("Any topic") })
                    repo.topics.forEach { t ->
                        SelectChip(selected = topic == t, onClick = { topic = t }, label = { Text(t) })
                    }
                }

                val q = question
                if (q == null) {
                    EmptyState("No questions available. Add some in the question bank.")
                    return@Column
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopicPill(q.topic)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "GRE pace: ${formatDuration(Gre.PACE_MS_PER_QUESTION)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                QuestionText(q.text)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.choices.forEachIndexed { c, text ->
                        ChoiceRow(
                            index = c,
                            text = text,
                            state = choiceState(c, selected, q.correctIndex, checked),
                            onClick = if (checked) null else ({ selected = if (selected == c) null else c }),
                        )
                    }
                }
                if (checked) {
                    ExplanationBox(q.explanation, selected == q.correctIndex)
                    Text(
                        "Answered in ${formatDuration(stoppedMs)}. Saved to history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Keys: A–E or 1–5 choose · Enter check / next",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
