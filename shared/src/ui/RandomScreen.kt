package kz.arctan.grepractice.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.data.Repository
import kz.arctan.grepractice.data.newId
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.model.AnswerRecord
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.practice.Pick
import kz.arctan.grepractice.practice.PickReason
import kz.arctan.grepractice.practice.QuestionPicker
import kz.arctan.grepractice.practice.formatDuration
import kz.arctan.grepractice.practice.predictedScaled

/** Recently shown question ids are skipped so the same question doesn't come up twice in a row. */
private const val RECENT_WINDOW = 5

/** Saves the shown question by id (with why it was picked); it's dropped if the question was deleted meanwhile. */
private fun pickSaver(repo: Repository) = Saver<Pick?, ArrayList<Any>>(
    save = { p -> p?.let { arrayListOf(it.question.id, it.reason.name, it.slowTopicAvgMs ?: -1L) } },
    restore = { v ->
        repo.questions.firstOrNull { it.id == v[0] }
            ?.let { Pick(it, PickReason.valueOf(v[1] as String), (v[2] as Long).takeIf { ms -> ms >= 0 }) }
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RandomScreen(vm: AppViewModel) {
    val repo = vm.repo
    val fb = LocalFeedbackColors.current
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    fun poolFor(t: String?) = repo.questions.filter { t == null || it.topic == t }
    val pool = poolFor(topic)

    val recent = rememberSaveable(saver = listSaver(save = { ArrayList(it) }, restore = { ArrayDeque(it) })) { ArrayDeque<String>() }
    fun draw(): Pick? {
        val candidates = poolFor(topic)
        if (candidates.isEmpty()) return null
        val avoid = recent.takeLast(minOf(RECENT_WINDOW, candidates.size - 1)).toSet()
        return QuestionPicker(repo.results).pick(candidates.filter { it.id !in avoid })?.also { recent.addLast(it.question.id) }
    }

    var pick by rememberSaveable(stateSaver = pickSaver(repo)) { mutableStateOf<Pick?>(null) }
    val question = pick?.question
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var shownAt by rememberSaveable { mutableLongStateOf(nowMillis()) }
    var now by remember { mutableLongStateOf(nowMillis()) }
    var stoppedMs by rememberSaveable { mutableLongStateOf(0L) }
    var attempted by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }

    fun next() {
        pick = draw()
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

    fun selectTopic(t: String?) {
        if (t == topic) return
        topic = t
        next()
    }

    // Draw only when nothing is shown: after a configuration change (e.g. rotation) the restored
    // question stays. Keyed on the pool so questions arriving later (first sync) get drawn too.
    LaunchedEffect(pool.isEmpty()) { if (pick == null) next() }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMillis()
            delay(250.milliseconds)
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
                        if (attempted == 0) "This sitting: no answers yet"
                        else "This sitting: $correct / $attempted correct" +
                            // From 10 answers on: what a full exam answered this well would score.
                            (predictedScaled(correct, attempted)?.let { " · predicted GRE ≈ $it" } ?: ""),
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
                    SelectChip(selected = topic == null, onClick = { selectTopic(null) }, label = { Text("Any topic") })
                    repo.topics.forEach { t ->
                        SelectChip(selected = topic == t, onClick = { selectTopic(t) }, label = { Text(t) })
                    }
                }

                val q = question
                if (q == null) {
                    EmptyState("No questions available. Add some in the question bank.")
                    return@Column
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopicPill(q.topic)
                    // Why this question came up, so the weighting is visible.
                    Text(
                        pick?.let { p -> p.reason.label + (p.slowTopicAvgMs?.let { " · slow topic, ${formatDuration(it)} avg" } ?: "") }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "GRE pace: ${formatDuration(Gre.PACE_MS_PER_QUESTION)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MissingFigureNote(q.text, q.choices)
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
