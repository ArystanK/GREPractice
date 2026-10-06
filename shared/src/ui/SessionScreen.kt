package kz.arctan.grepractice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import kz.arctan.grepractice.practice.PracticeSession
import kz.arctan.grepractice.practice.formatDuration

@Composable
fun SessionScreen(vm: AppViewModel) {
    val session = vm.session ?: return
    val config = session.config
    val fb = LocalFeedbackColors.current
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        while (true) {
            if (session.tick()) {
                vm.finishSession(timedOut = true)
                break
            }
            delay(250)
        }
    }
    PlatformBackHandler(enabled = true) { confirmQuit = true }

    val i = session.current
    val item = session.items[i]
    val isLast = i == session.items.lastIndex
    val revealed = config.instantFeedback && session.checked[i]

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Keyboard shortcuts; the focus target wraps the whole screen so it is attached before requestFocus runs.
    val onKey: (KeyEvent) -> Boolean = handler@{ e ->
        if (e.type != KeyEventType.KeyDown) return@handler false
        val choice = choiceForKey(e.key)
        when {
            choice != null && choice < item.choices.size -> session.select(choice)
            e.key == Key.DirectionRight -> session.goTo(i + 1)
            e.key == Key.DirectionLeft -> session.goTo(i - 1)
            e.key == Key.F -> session.toggleFlag()
            e.key == Key.Enter && config.instantFeedback && !session.checked[i] -> session.check()
            e.key == Key.Enter -> if (isLast) confirmSubmit = true else session.goTo(i + 1)
            else -> return@handler false
        }
        true
    }

    Box(Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent(onKey)) {
        ScreenScaffold(
            title = config.title,
            onBack = { confirmQuit = true },
            actions = { TimerLabel(session) },
            bottomBar = {
                CenteredBar {
                    OutlinedButton(onClick = { session.goTo(i - 1) }, enabled = i > 0) { Text("← Prev") }
                    Spacer(Modifier.weight(1f))
                    if (config.instantFeedback && !session.checked[i]) {
                        FilledTonalButton(onClick = session::check, enabled = session.selected[i] != null) { Text("Check") }
                    }
                    if (isLast) {
                        Button(onClick = { confirmSubmit = true }) { Text("Finish") }
                    } else {
                        Button(onClick = { session.goTo(i + 1) }) { Text("Next →") }
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
                config.timeLimitMs?.let { limit ->
                    val remaining = session.remainingMs ?: 0
                    LinearProgressIndicator(
                        progress = { remaining.toFloat() / limit },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (remaining < limit / 10) fb.incorrect else MaterialTheme.colorScheme.primary,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Question ${i + 1} of ${session.items.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TopicPill(item.question.topic)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "⏱ ${formatDuration(session.currentQuestionElapsedMs)}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = session::toggleFlag) {
                        Text(
                            if (session.flagged[i]) "⚑ Flagged" else "⚐ Flag",
                            color = if (session.flagged[i]) fb.flag else MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                MissingFigureNote(item.question.text, item.choices)
                QuestionText(item.question.text)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.choices.forEachIndexed { c, text ->
                        ChoiceRow(
                            index = c,
                            text = text,
                            state = choiceState(c, session.selected[i], item.correctIndex, revealed),
                            onClick = if (revealed) null else ({ session.select(c) }),
                        )
                    }
                }

                if (revealed) {
                    ExplanationBox(item.question.explanation, session.selected[i] == item.correctIndex)
                }

                Navigator(session)

                Text(
                    "Keys: A–E or 1–5 choose · ←/→ move · F flag · Enter ${if (config.instantFeedback) "check / " else ""}next",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmSubmit) {
        val unanswered = session.items.size - session.answeredCount
        val flagged = session.flagged.count { it }
        ConfirmDialog(
            title = "Finish and see results?",
            text = buildString {
                append("You've answered ${session.answeredCount} of ${session.items.size} questions.")
                if (unanswered > 0) append(" $unanswered unanswered will count as incorrect.")
                if (flagged > 0) append(" $flagged flagged for review.")
            },
            confirmLabel = "Finish",
            onConfirm = { confirmSubmit = false; vm.finishSession() },
            onDismiss = { confirmSubmit = false },
            dismissLabel = "Keep going",
        )
    }
    if (confirmQuit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Leave this session?") },
            text = { Text("You can submit now to save your result, or discard the session entirely.") },
            confirmButton = {
                TextButton(onClick = { confirmQuit = false; vm.finishSession() }) { Text("Submit & save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmQuit = false; vm.abandonSession() }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { confirmQuit = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun TimerLabel(session: PracticeSession) {
    val remaining = session.remainingMs
    val limit = session.config.timeLimitMs
    val low = remaining != null && limit != null && remaining < limit / 10
    Text(
        text = remaining?.let { "${formatDuration(it)} left" } ?: formatDuration(session.elapsedMs),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = if (low) LocalFeedbackColors.current.incorrect else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 16.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Navigator(session: PracticeSession) {
    val fb = LocalFeedbackColors.current
    val cs = MaterialTheme.colorScheme
    SectionCard(title = "Questions") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            session.items.forEachIndexed { idx, item ->
                val answered = session.selected[idx] != null
                val checked = session.config.instantFeedback && session.checked[idx]
                val bg = when {
                    checked && session.selected[idx] == item.correctIndex -> fb.correctContainer
                    checked -> fb.incorrectContainer
                    answered -> cs.primaryContainer
                    else -> cs.surface
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(
                            width = if (idx == session.current) 2.dp else 1.dp,
                            color = when {
                                idx == session.current -> cs.primary
                                session.flagged[idx] -> fb.flag
                                else -> cs.outlineVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { session.goTo(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${idx + 1}",
                        fontWeight = if (idx == session.current) FontWeight.Bold else FontWeight.Normal,
                        color = if (session.flagged[idx]) fb.flag else Color.Unspecified,
                    )
                }
            }
        }
        Text(
            "${session.answeredCount} answered · ${session.flagged.count { it }} flagged · ${session.items.size - session.answeredCount} left",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

internal fun choiceForKey(key: Key): Int? = when (key) {
    Key.A, Key.One, Key.NumPad1 -> 0
    Key.B, Key.Two, Key.NumPad2 -> 1
    Key.C, Key.Three, Key.NumPad3 -> 2
    Key.D, Key.Four, Key.NumPad4 -> 3
    Key.E, Key.Five, Key.NumPad5 -> 4
    else -> null
}
