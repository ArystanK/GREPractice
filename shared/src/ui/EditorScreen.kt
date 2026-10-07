package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import kz.arctan.grepractice.data.ImageStore
import kz.arctan.grepractice.data.imageMarkup
import kz.arctan.grepractice.model.Question
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import kz.arctan.grepractice.ui.math.LatexFormula
import kz.arctan.grepractice.ui.math.MathText
import kz.arctan.grepractice.ui.math.isInsideMath

private const val MIN_CHOICES = 2
private const val MAX_CHOICES = 8

/**
 * A LaTeX snippet for the palette: [label] is rendered on the button and [insert] goes into the
 * field. The cursor lands at a `‸` marker, else inside the first `{}` / `[]`, else after the snippet.
 */
private class Snippet(val label: String, insert: String = label) {
    val insert: String = insert.replace("‸", "")
    val cursor: Int = insert.indexOf('‸').takeIf { it >= 0 }
        ?: listOf(insert.indexOf("{}"), insert.indexOf("[]")).filter { it >= 0 }.minOrNull()?.plus(1)
        ?: insert.length
}

/** Argument-less commands get a trailing space so a following letter doesn't merge into the command name. */
private fun cmd(vararg names: String) = names.map { Snippet("\\$it", "\\$it ") }

private val Snippets: List<Snippet> = listOf(
    Snippet("x^{n}", "^{}"), Snippet("x_{n}", "_{}"), Snippet("\\frac{a}{b}", "\\frac{}{}"),
    Snippet("\\sqrt{x}", "\\sqrt{}"), Snippet("\\sqrt[n]{x}", "\\sqrt[]{}"),
    Snippet("\\int_a^b", "\\int_{}^{} "), Snippet("\\oint", "\\oint_{} "), Snippet("\\iint", "\\iint_{} "),
    Snippet("\\sum", "\\sum_{}^{} "), Snippet("\\prod", "\\prod_{}^{} "), Snippet("\\lim", "\\lim_{x \\to ‸} "),
    Snippet("\\binom{n}{k}", "\\binom{}{}"), Snippet("|x|", "\\lvert ‸ \\rvert"), Snippet("(x)", "\\left( ‸ \\right)"),
    Snippet("\\overline{x}", "\\overline{}"), Snippet("\\hat{x}", "\\hat{}"), Snippet("\\vec{v}", "\\vec{}"),
    Snippet("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}", "\\begin{pmatrix} ‸ &  \\\\  &  \\end{pmatrix}"),
    Snippet("\\mathbb{R}"), Snippet("\\mathbb{Z}"), Snippet("\\mathbb{Q}"), Snippet("\\mathbb{C}"), Snippet("\\mathbb{N}"),
) + cmd(
    "infty", "pi", "pm", "cdot", "times", "le", "ge", "ne", "approx", "equiv", "to", "mapsto", "Rightarrow", "iff",
    "in", "notin", "subseteq", "subset", "cup", "cap", "emptyset", "forall", "exists", "partial", "nabla",
    "sin", "cos", "tan", "ln", "log", "det", "dim", "ker",
    "alpha", "beta", "gamma", "delta", "varepsilon", "theta", "lambda", "mu", "sigma", "varphi", "omega", "Delta", "Sigma", "Omega",
)

private val TextFieldListSaver = listSaver<SnapshotStateList<TextFieldValue>, Any>(
    save = { list -> list.mapNotNull { with(TextFieldValue.Saver) { save(it) } } },
    restore = { saved -> saved.mapNotNull { TextFieldValue.Saver.restore(it) }.toMutableStateList() },
)

private fun TextFieldValue.insertSnippet(snippet: Snippet): TextFieldValue {
    val start = selection.min
    // Outside math, wrap the snippet in $…$ so it renders.
    val wrap = !isInsideMath(text, start)
    val inserted = if (wrap) "$" + snippet.insert + "$" else snippet.insert
    val cursor = start + snippet.cursor + if (wrap) 1 else 0
    return TextFieldValue(text.replaceRange(start, selection.max, inserted), TextRange(cursor))
}

/** Wraps the selection in `$…$`, or inserts an empty pair with the cursor inside. */
private fun TextFieldValue.wrapMath(): TextFieldValue {
    val sel = text.substring(selection.min, selection.max)
    return TextFieldValue(text.replaceRange(selection.min, selection.max, "$" + sel + "$"), TextRange(selection.min + 1 + sel.length))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: AppViewModel, questionId: String?) {
    val repo = vm.repo
    val existing = remember(questionId) { repo.questions.firstOrNull { it.id == questionId } }

    // Saveable, so a rotation or the system reclaiming the activity during image picking keeps the draft.
    var topic by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(existing?.topic ?: "")) }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(existing?.text ?: "")) }
    val choices = rememberSaveable(saver = TextFieldListSaver) {
        mutableStateListOf<TextFieldValue>().apply {
            (existing?.choices ?: List(5) { "" }).forEach { add(TextFieldValue(it)) }
        }
    }
    var correct by rememberSaveable { mutableIntStateOf(existing?.correctIndex ?: -1) }
    var explanation by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(existing?.explanation ?: "")) }
    /** Which field the LaTeX palette edits: "text", "expl", or a choice index as a string. */
    var activeField by rememberSaveable { mutableStateOf("text") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val filledChoices = choices.count { it.text.isNotBlank() }
    val errors = buildList {
        if (topic.text.isBlank()) add("Enter a topic.")
        if (text.text.isBlank()) add("Enter the question text.")
        if (choices.any { it.text.isBlank() }) add("Fill in every choice or remove the empty ones.")
        if (filledChoices < MIN_CHOICES) add("Add at least $MIN_CHOICES choices.")
        if (correct !in choices.indices) add("Mark the correct answer with the radio button.")
    }

    fun editActive(edit: (TextFieldValue) -> TextFieldValue) {
        when (activeField) {
            "text" -> text = edit(text)
            "expl" -> explanation = edit(explanation)
            else -> activeField.toIntOrNull()?.takeIf { it in choices.indices }?.let { choices[it] = edit(choices[it]) }
        }
    }

    var imageMessage by rememberSaveable { mutableStateOf<String?>(null) }

    /** Stores the image and inserts its markup at the cursor of the focused field. */
    fun insertImage(result: Result<PickedImage?>) {
        imageMessage = null
        val picked = result.getOrElse { imageMessage = it.message ?: "Couldn't add the image."; return } ?: return
        val name = runCatching { ImageStore.add(picked.bytes, picked.extension) }
            .getOrElse { imageMessage = it.message ?: "Couldn't add the image."; return }
        // In the question text a figure goes on its own line; in a choice it can stand alone.
        val markup = imageMarkup(name, alt = if (activeField == "text" || activeField == "expl") "figure" else "graph")
        editActive { field ->
            val start = field.selection.min
            val before = field.text.substring(0, start)
            val insert = if (activeField == "text" && before.isNotEmpty() && !before.endsWith("\n")) "\n$markup\n" else markup
            TextFieldValue(field.text.replaceRange(start, field.selection.max, insert), TextRange(start + insert.length))
        }
    }

    val pickImage = rememberImagePicker(::insertImage)

    fun save() {
        if (errors.isNotEmpty()) {
            showErrors = true
            return
        }
        vm.saveQuestion(
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

    val isShared = existing != null && repo.isShared(existing.id)
    val readOnly = isShared && !vm.isAdmin

    ScreenScaffold(
        title = when {
            existing == null -> "Add question"
            readOnly -> "Shared question"
            else -> "Edit question"
        },
        onBack = vm::back,
        bottomBar = {
            CenteredBar {
                if (existing != null && !readOnly) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Text(
                    if (showErrors && errors.isNotEmpty()) errors.first() else "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = vm::back) { Text(if (readOnly) "Close" else "Cancel") }
                if (!readOnly) Button(onClick = ::save) { Text(if (isShared) "Save for everyone" else "Save") }
            }
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isShared) {
                SectionCard {
                    Text(
                        if (readOnly) {
                            "This question is in the shared bank, so it can't be changed here. Only admins can edit shared questions."
                        } else {
                            "This question is in the shared bank. Saving or deleting it changes it for every user."
                        },
                        color = if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            SectionCard(title = "Topic") {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    singleLine = true,
                    isError = showErrors && topic.text.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
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
                Text(
                    "Write math in LaTeX: \$…\$ inline, \$\$…\$\$ on its own line, \\\$ for a literal dollar sign. " +
                        "This works in the question, the choices and the explanation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MathPalette(onSnippet = { sn -> editActive { it.insertSnippet(sn) } }, onWrap = { editActive { it.wrapMath() } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = pickImage) { Text("Add image…") }
                    if (canPasteImage) {
                        OutlinedButton(onClick = {
                            val pasted = runCatching { pasteImageFromClipboard() }
                            if (pasted.getOrNull() == null && pasted.isSuccess) {
                                imageMessage = "There's no image on the clipboard. Copy a screenshot of the figure first."
                            } else {
                                insertImage(pasted)
                            }
                        }) { Text("Paste image") }
                    }
                }
                Text(
                    imageMessage ?: "Figures are inserted into the focused field: the question, a choice (for graph answers) or the explanation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (imageMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            SectionCard(title = "Preview") {
                if (text.text.isBlank()) {
                    Text("The rendered question appears here as you type.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MissingFigureNote(text.text, choices.map { it.text })
                    QuestionText(text.text)
                }
                choices.forEachIndexed { i, value ->
                    if (value.text.isNotBlank()) {
                        ChoiceRow(i, value.text, if (i == correct) ChoiceState.MissedCorrect else ChoiceState.Normal, onClick = null)
                    }
                }
                if (explanation.text.isNotBlank()) MathText(explanation.text, style = MaterialTheme.typography.bodyLarge)
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
                vm.deleteQuestion(existing.id)
                confirmDelete = false
                vm.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MathPalette(onSnippet: (Snippet) -> Unit, onWrap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Insert into the focused field (\$…\$ is added automatically outside math):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PaletteButton(onClick = onWrap) { Text("\$…\$", fontWeight = FontWeight.Bold) }
            Snippets.forEach { sn ->
                PaletteButton(onClick = { onSnippet(sn) }) {
                    LatexFormula(sn.label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun PaletteButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.heightIn(min = 36.dp).widthIn(min = 36.dp),
    ) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) { content() }
    }
}
