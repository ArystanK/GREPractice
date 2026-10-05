package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.data.dataLocation

private val ImportExample = """
[
  {
    "topic": "Calculus",
    "text": "∫₀¹ 2x dx =",
    "choices": ["0", "1/2", "1", "2", "4"],
    "answer": "C",
    "explanation": "x² from 0 to 1 is 1."
  }
]
""".trimIndent()

@Suppress("DEPRECATION") // LocalClipboardManager is still the simplest cross-platform way to copy plain text.
@Composable
fun TransferScreen(vm: AppViewModel) {
    val repo = vm.repo
    val clipboard = LocalClipboardManager.current
    var importText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Import / export", onBack = vm::back) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            message?.let { (ok, text) ->
                SectionCard {
                    Text(text, color = if (ok) LocalFeedbackColors.current.correct else MaterialTheme.colorScheme.error)
                }
            }

            SectionCard(title = "Import questions") {
                Text(
                    "Paste a JSON array. Each question needs topic, text, choices, and either \"answer\" (a letter) or " +
                        "\"correctIndex\" (0-based). An optional \"id\" that matches an existing question replaces it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer {
                    Text(ImportExample, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("JSON to import") },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = importText.isNotBlank(),
                        onClick = {
                            message = runCatching { repo.importQuestionsJson(importText) }.fold(
                                onSuccess = { n -> importText = ""; true to "Imported $n question${if (n == 1) "" else "s"}." },
                                onFailure = { false to "Import failed: ${it.message}" },
                            )
                        },
                    ) { Text("Import") }
                    OutlinedButton(onClick = { clipboard.getText()?.let { importText = it.text } }) { Text("Paste from clipboard") }
                }
            }

            SectionCard(title = "Export question bank") {
                Text("${repo.questions.size} questions. The JSON below can be imported on another device.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(repo.exportQuestionsJson()))
                        message = true to "Copied ${repo.questions.size} questions to the clipboard."
                    }) { Text("Copy JSON") }
                }
            }

            SectionCard(title = "Data") {
                Text("Questions and results are saved automatically in:", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(dataLocation(), fontFamily = FontFamily.Monospace) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val n = repo.restoreSamples()
                        message = true to if (n == 0) "All sample questions are already in the bank." else "Restored $n sample questions."
                    }) { Text("Restore sample questions") }
                    OutlinedButton(onClick = { confirmClear = true }, enabled = repo.results.isNotEmpty()) {
                        Text("Clear practice history", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all practice history?",
            text = "This permanently deletes ${repo.results.size} saved sessions. Your questions are not affected.",
            confirmLabel = "Clear history",
            onConfirm = { repo.clearResults(); confirmClear = false; message = true to "Practice history cleared." },
            onDismiss = { confirmClear = false },
        )
    }
}
