package kz.arctan.grepractice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.Screen
import kz.arctan.grepractice.data.formatDateTime
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.model.PracticeResult
import kz.arctan.grepractice.practice.formatDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val repo = vm.repo
    var mode by rememberSaveable { mutableStateOf<PracticeMode?>(null) }
    var toDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val toDelete = toDeleteId?.let { id -> repo.results.firstOrNull { it.id == id } }
    val shown = repo.results.filter { mode == null || it.mode == mode }

    ScreenScaffold(title = "History", onBack = vm::back) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(selected = mode == null, onClick = { mode = null }, label = { Text("All (${repo.results.size})") })
                    PracticeMode.entries.forEach { m ->
                        val n = repo.results.count { it.mode == m }
                        if (n > 0) SelectChip(selected = mode == m, onClick = { mode = m }, label = { Text("${m.label} ($n)") })
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState("No saved sessions yet.") }
            items(shown, key = { it.id }) { r -> ResultRow(r, onOpen = { vm.navigate(Screen.Result(r.id)) }, onDelete = { toDeleteId = r.id }) }
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = "Delete this result?",
            text = "${r.title} — ${formatDateTime(r.startedAt)}",
            confirmLabel = "Delete",
            onConfirm = { repo.deleteResult(r.id); toDeleteId = null },
            onDismiss = { toDeleteId = null },
        )
    }
}

@Composable
private fun ResultRow(r: PracticeResult, onOpen: () -> Unit, onDelete: () -> Unit) {
    val fb = LocalFeedbackColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${r.mode.label} · ${formatDateTime(r.startedAt)} · ${formatDuration(r.durationMs)}" +
                        if (r.timedOut) " · timed out" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${r.correct}/${r.total}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        r.percent >= 70 -> fb.correct
                        r.percent < 40 -> fb.incorrect
                        else -> Color.Unspecified
                    },
                )
                Text("${r.percent}%", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}
