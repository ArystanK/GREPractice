package kz.arctan.grepractice.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.data.needsFigure
import kz.arctan.grepractice.ui.math.MathText

val MaxContentWidth = 860.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("← Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                content(PaddingValues(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

/** Constrains a bottom bar's contents to the same width as the screen content. */
@Composable
fun CenteredBar(content: @Composable RowScope.() -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSecondaryContainer else valueColor,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

enum class ChoiceState { Normal, Selected, Correct, Incorrect, MissedCorrect }

/** One answer choice with its letter badge, colored by [state]. */
@Composable
fun ChoiceRow(index: Int, text: String, state: ChoiceState, onClick: (() -> Unit)?) {
    val fb = LocalFeedbackColors.current
    val cs = MaterialTheme.colorScheme
    val (container, border, badge) = when (state) {
        ChoiceState.Normal -> Triple(cs.surface, cs.outlineVariant, cs.surfaceVariant)
        ChoiceState.Selected -> Triple(cs.primaryContainer, cs.primary, cs.primary)
        ChoiceState.Correct -> Triple(fb.correctContainer, fb.correct, fb.correct)
        ChoiceState.MissedCorrect -> Triple(cs.surface, fb.correct, fb.correct)
        ChoiceState.Incorrect -> Triple(fb.incorrectContainer, fb.incorrect, fb.incorrect)
    }
    val badgeText = if (state == ChoiceState.Normal) cs.onSurfaceVariant else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(BorderStroke(if (state == ChoiceState.Normal) 1.dp else 2.dp, border), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Text(Gre.letter(index), color = badgeText, fontWeight = FontWeight.Bold)
        }
        MathText(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
            modifier = Modifier.weight(1f),
            imageMaxHeight = 200.dp,
        )
        when (state) {
            ChoiceState.Correct, ChoiceState.MissedCorrect -> Text("✓", color = fb.correct, fontWeight = FontWeight.Bold)
            ChoiceState.Incorrect -> Text("✗", color = fb.incorrect, fontWeight = FontWeight.Bold)
            else -> {}
        }
    }
}

fun choiceState(index: Int, selected: Int?, correct: Int, revealed: Boolean): ChoiceState = when {
    !revealed -> if (index == selected) ChoiceState.Selected else ChoiceState.Normal
    index == correct && index == selected -> ChoiceState.Correct
    index == correct -> ChoiceState.MissedCorrect
    index == selected -> ChoiceState.Incorrect
    else -> ChoiceState.Normal
}

@Composable
fun QuestionText(text: String) {
    MathText(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 30.sp))
}

/** Shown above a question whose wording refers to a figure that hasn't been added to it. */
@Composable
fun MissingFigureNote(text: String, choices: List<String>) {
    if (!needsFigure(text, choices)) return
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "This question refers to a figure that hasn't been added. You can add it in the question bank's editor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun TopicPill(topic: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            topic,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ExplanationBox(explanation: String, correct: Boolean?) {
    val fb = LocalFeedbackColors.current
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (correct) {
                true -> Text("Correct!", color = fb.correct, fontWeight = FontWeight.Bold)
                false -> Text("Incorrect", color = fb.incorrect, fontWeight = FontWeight.Bold)
                null -> Text("Not answered", fontWeight = FontWeight.Bold)
            }
            if (explanation.isNotBlank()) MathText(explanation, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

/** FilterChip with a visible check mark when selected. */
@Composable
fun SelectChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = if (selected) ({ Text("✓", fontWeight = FontWeight.Bold) }) else null,
    )
}
