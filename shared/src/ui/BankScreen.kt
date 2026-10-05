package kz.arctan.grepractice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.padding
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.ui.math.MathText
import kz.arctan.grepractice.ui.math.mathToPlain

@Composable
fun BankScreen(vm: AppViewModel) {
    val repo = vm.repo
    var search by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Question?>(null) }

    // Latest-attempt stats per question id.
    val history = remember(repo.results.size) {
        repo.results.flatMap { it.answers }.groupBy { it.questionId }
    }
    val shown = repo.questions
        .filter { topic == null || it.topic == topic }
        .filter { search.isBlank() || it.text.contains(search, ignoreCase = true) || it.topic.contains(search, ignoreCase = true) }
        .sortedWith(compareBy({ it.topic }, { it.createdAt }))

    ScreenScaffold(
        title = "Question bank",
        onBack = vm::back,
        actions = { TextButton(onClick = { vm.navigate(Screen.Editor(null)) }) { Text("+ Add question") } },
    ) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search questions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectChip(selected = topic == null, onClick = { topic = null }, label = { Text("All (${repo.questions.size})") }) }
                    items(repo.topics) { t ->
                        SelectChip(
                            selected = topic == t,
                            onClick = { topic = if (topic == t) null else t },
                            label = { Text("$t (${repo.questions.count { it.topic == t }})") },
                        )
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState(if (repo.questions.isEmpty()) "No questions yet — add one or import JSON." else "No matches.") }
            items(shown, key = { it.id }) { q ->
                val attempts = history[q.id].orEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { vm.navigate(Screen.Editor(q.id)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopicPill(q.topic)
                            Text(
                                "Answer ${Gre.letter(q.correctIndex)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (attempts.isNotEmpty()) {
                                Text(
                                    "· ${attempts.count { it.isCorrect }}/${attempts.size} correct",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { toDelete = q }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                        MathText(q.text, maxLines = 3)
                    }
                }
            }
        }
    }

    toDelete?.let { q ->
        ConfirmDialog(
            title = "Delete question?",
            text = mathToPlain(q.text).take(140),
            confirmLabel = "Delete",
            onConfirm = { repo.deleteQuestion(q.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}
