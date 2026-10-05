package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.data.newId
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.model.Gre
import kz.arctan.grepractice.model.Question

private const val MIN_CHOICES = 2
private const val MAX_CHOICES = 8

/** Unicode symbols for writing math without LaTeX. */
private val Symbols = listOf(
    "²", "³", "ⁿ", "⁻¹", "₀", "₁", "₂", "ₙ", "√", "∛", "π", "e", "∞", "±", "·", "×", "÷",
    "≤", "≥", "≠", "≈", "≡", "→", "↦", "⇒", "⇔", "∫", "∮", "∑", "∏", "∂", "∇", "lim",
    "∈", "∉", "⊂", "⊆", "∪", "∩", "∅", "∀", "∃", "ℕ", "ℤ", "ℚ", "ℝ", "ℂ", "|z|",
    "α", "β", "γ", "δ", "ε", "θ", "λ", "μ", "σ", "φ", "ω", "Δ", "Σ", "Ω", "°", "′", "″",
)

private fun TextFieldValue.insert(s: String): TextFieldValue {
    val start = selection.min
    return TextFieldValue(text.replaceRange(start, selection.max, s), TextRange(start + s.length))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: AppViewModel, questionId: String?) {
    val repo = vm.repo
    val existing = remember(questionId) { repo.questions.firstOrNull { it.id == questionId } }

    var topic by remember { mutableStateOf(TextFieldValue(existing?.topic ?: "")) }
    var text by remember { mutableStateOf(TextFieldValue(existing?.text ?: "")) }
    val choices = remember {
        mutableStateListOf<TextFieldValue>().apply {
            (existing?.choices ?: List(5) { "" }).forEach { add(TextFieldValue(it)) }
        }
    }
    var correct by remember { mutableIntStateOf(existing?.correctIndex ?: -1) }
    var explanation by remember { mutableStateOf(TextFieldValue(existing?.explanation ?: "")) }
    /** Which field the symbol palette inserts into: "topic", "text", "expl", or a choice index as a string. */
    var activeField by remember { mutableStateOf("text") }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val filledChoices = choices.count { it.text.isNotBlank() }
    val errors = buildList {
        if (topic.text.isBlank()) add("Enter a topic.")
        if (text.text.isBlank()) add("Enter the question text.")
        if (choices.any { it.text.isBlank() }) add("Fill in every choice or remove the empty ones.")
        if (filledChoices < MIN_CHOICES) add("Add at least $MIN_CHOICES choices.")
        if (correct !in choices.indices) add("Mark the correct answer with the radio button.")
    }

    fun insertSymbol(s: String) {
        when (activeField) {
            "topic" -> topic = topic.insert(s)
            "text" -> text = text.insert(s)
            "expl" -> explanation = explanation.insert(s)
            else -> activeField.toIntOrNull()?.takeIf { it in choices.indices }?.let { choices[it] = choices[it].insert(s) }
        }
    }

    fun save() {
        if (errors.isNotEmpty()) {
            showErrors = true
            return
        }
        repo.upsertQuestion(
            Question(
                id = existing?.id ?: newId(),
                topic = topic.text.trim(),
                text = text.text.trim(),
                choices = choices.map { it.text.trim() },
                correctIndex = correct,
                explanation = explanation.text.trim(),
                createdAt = existing?.createdAt ?: nowMillis(),
            ),
        )
        vm.back()
    }

    ScreenScaffold(
        title = if (existing == null) "Add question" else "Edit question",
        onBack = vm::back,
        bottomBar = {
            CenteredBar {
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Text(
                    if (showErrors && errors.isNotEmpty()) errors.first() else "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = vm::back) { Text("Cancel") }
                Button(onClick = ::save) { Text("Save") }
            }
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Topic") {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    singleLine = true,
                    isError = showErrors && topic.text.isBlank(),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeField = "topic" },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (repo.topics + Gre.defaultTopics).distinct().sorted().forEach { t ->
                        AssistChip(onClick = { topic = TextFieldValue(t, TextRange(t.length)) }, label = { Text(t) })
                    }
                }
            }

            SectionCard(title = "Question") {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Question text") },
                    minLines = 3,
                    isError = showErrors && text.text.isBlank(),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeField = "text" },
                )
                SymbolPalette(::insertSymbol)
            }

            SectionCard(title = "Answer choices") {
                Text(
                    "Select the radio button next to the correct answer. GRE questions have five choices (A–E).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choices.forEachIndexed { i, value ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadioButton(selected = correct == i, onClick = { correct = i })
                        OutlinedTextField(
                            value = value,
                            onValueChange = { choices[i] = it },
                            label = { Text("Choice ${Gre.letter(i)}" + if (correct == i) " — correct" else "") },
                            singleLine = true,
                            isError = showErrors && value.text.isBlank(),
                            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) activeField = i.toString() },
                        )
                        TextButton(
                            onClick = {
                                choices.removeAt(i)
                                correct = when {
                                    correct == i -> -1
                                    correct > i -> correct - 1
                                    else -> correct
                                }
                            },
                            enabled = choices.size > MIN_CHOICES,
                        ) { Text("✕") }
                    }
                }
                if (choices.size < MAX_CHOICES) {
                    TextButton(onClick = { choices.add(TextFieldValue("")) }) { Text("+ Add choice") }
                }
            }

            SectionCard(title = "Explanation (optional)") {
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Shown after answering") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeField = "expl" },
                )
            }

            if (showErrors && errors.isNotEmpty()) {
                SectionCard {
                    errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete question?",
            text = "This removes it from the bank. Past results that include it are kept.",
            confirmLabel = "Delete",
            onConfirm = {
                repo.deleteQuestion(existing.id)
                confirmDelete = false
                vm.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolPalette(onInsert: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Insert symbol into the focused field:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Symbols.forEach { s ->
                Surface(
                    onClick = { onInsert(s) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(width = if (s.length > 2) 44.dp else 34.dp, height = 34.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(s, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
