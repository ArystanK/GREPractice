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
import androidx.compose.material3.Button
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.data.needsFigure
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.ui.math.MathText
import kz.arctan.grepractice.ui.math.mathToPlain

@Composable
fun BankScreen(vm: AppViewModel) {
    val repo = vm.repo
    var search by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var toDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val toDelete = toDeleteId?.let { id -> repo.questions.firstOrNull { it.id == id } }
    var confirmDedupe by rememberSaveable { mutableStateOf(false) }
    var dedupeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // Recomputed whenever the question list changes, including changes pulled in by sync.
    val duplicateGroups by remember { derivedStateOf { repo.duplicateGroups() } }
    // Only the user's own copies can be removed; shared-bank questions always stay.
    val duplicateCount = duplicateGroups.sumOf { g -> g.drop(1).count { !repo.isShared(it.id) } }
    /** null = all, true = shared bank, false = the user's own. */
    var origin by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val sharedCount = repo.sharedIds.size
    val ownCount = repo.questions.size - sharedCount
    var confirmPublish by rememberSaveable { mutableStateOf(false) }

    // Latest-attempt stats per question id.
    val history = remember(repo.results.size) {
        repo.results.flatMap { it.answers }.groupBy { it.questionId }
    }
    var needsFigureOnly by rememberSaveable { mutableStateOf(false) }
    val needingFigure by remember { derivedStateOf { repo.questions.count { it.needsFigure() } } }
    val shown = repo.questions
        .filter { topic == null || it.topic == topic }
        .filter { origin == null || repo.isShared(it.id) == origin }
        .filter { !needsFigureOnly || it.needsFigure() }
        .filter { search.isBlank() || it.text.contains(search, ignoreCase = true) || it.topic.contains(search, ignoreCase = true) }
        .sortedWith(compareBy({ it.topic }, { it.createdAt }))

    ScreenScaffold(
        title = "Question bank",
        onBack = vm::back,
        actions = { TextButton(onClick = { vm.navigate(Screen.Editor(null)) }) { Text("+ Add question") } },
    ) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.isAdmin || vm.bankMessage != null) {
                item {
                    SectionCard(title = if (vm.isAdmin) "Shared bank (admin)" else null) {
                        if (vm.isAdmin) {
                            Text(
                                "Edits you make to shared questions are published to everyone. " +
                                    if (ownCount > 0) "$ownCount of your own questions aren't in the shared bank yet." else "All your questions are in the shared bank.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (ownCount > 0) Button(onClick = { confirmPublish = true }) { Text("Publish my questions to the shared bank") }
                        }
                        vm.bankMessage?.let { (ok, text) ->
                            Text(text, color = if (ok) LocalFeedbackColors.current.correct else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (duplicateCount > 0 || dedupeMessage != null) {
                item {
                    SectionCard {
                        if (duplicateCount > 0) {
                            Text(
                                "$duplicateCount duplicate question${if (duplicateCount == 1) "" else "s"}: the same text, choices and answer stored more than once.",
                                fontWeight = FontWeight.Medium,
                            )
                            Button(onClick = { confirmDedupe = true }) { Text("Remove duplicates") }
                        }
                        dedupeMessage?.let { Text(it, color = LocalFeedbackColors.current.correct) }
                    }
                }
            }
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
                    if (sharedCount > 0) {
                        item { SelectChip(selected = origin == true, onClick = { origin = if (origin == true) null else true }, label = { Text("Shared ($sharedCount)") }) }
                        item { SelectChip(selected = origin == false, onClick = { origin = if (origin == false) null else false }, label = { Text("Mine ($ownCount)") }) }
                    }
                    if (needingFigure > 0 || needsFigureOnly) {
                        item {
                            SelectChip(
                                selected = needsFigureOnly,
                                onClick = { needsFigureOnly = !needsFigureOnly },
                                label = { Text("Needs figure ($needingFigure)") },
                            )
                        }
                    }
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
                            val isShared = repo.isShared(q.id)
                            if (isShared) {
                                Text("Shared", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
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
                            if (!isShared || vm.isAdmin) {
                                TextButton(onClick = { toDeleteId = q.id }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        MathText(q.text, maxLines = 3, imageMaxHeight = 120.dp)
                    }
                }
            }
        }
    }

    toDelete?.let { q ->
        ConfirmDialog(
            title = if (repo.isShared(q.id)) "Delete from the shared bank?" else "Delete question?",
            text = (if (repo.isShared(q.id)) "This removes it for every user. " else "") + mathToPlain(q.text).take(140),
            confirmLabel = "Delete",
            onConfirm = { vm.deleteQuestion(q.id); toDeleteId = null },
            onDismiss = { toDeleteId = null },
        )
    }

    if (confirmPublish) {
        ConfirmDialog(
            title = "Publish $ownCount question${if (ownCount == 1) "" else "s"}?",
            text = "They move from your own questions into the shared bank, with their figures, and every user of the app " +
                "will see them. You can still edit or delete them as an admin.",
            confirmLabel = "Publish",
            onConfirm = {
                confirmPublish = false
                vm.publishOwn(repo.ownQuestions().map { it.id }.toSet())
            },
            onDismiss = { confirmPublish = false },
        )
    }

    if (confirmDedupe) {
        ConfirmDialog(
            title = "Remove $duplicateCount duplicate${if (duplicateCount == 1) "" else "s"}?",
            text = "One copy of each question is kept: the practice-test copy if there is one, otherwise the copy with an " +
                "explanation, otherwise the oldest. Past results are not affected, and the removal syncs to your other devices.",
            confirmLabel = "Remove",
            onConfirm = {
                val removed = repo.removeDuplicates()
                confirmDedupe = false
                dedupeMessage = "Removed $removed duplicate${if (removed == 1) "" else "s"}."
            },
            onDismiss = { confirmDedupe = false },
        )
    }
}
