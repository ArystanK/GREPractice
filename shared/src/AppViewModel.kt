package kz.arctan.grepractice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kz.arctan.grepractice.data.Repository
import kz.arctan.grepractice.data.cloud.CloudBackend
import kz.arctan.grepractice.data.cloud.CloudException
import kz.arctan.grepractice.data.cloud.SyncEngine
import kz.arctan.grepractice.data.cloud.SyncReport
import kz.arctan.grepractice.model.Question
import kz.arctan.grepractice.data.cloud.createCloudBackend
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.PracticeSession

sealed interface Screen {
    data object Home : Screen
    /** "Simulated exam": the bank's full practice tests plus the random exam. */
    data object Exams : Screen
    /** [exam] = randomly assembled full exam; otherwise custom/topic practice, optionally preselecting [topic]. */
    data class Setup(val exam: Boolean, val topic: String? = null) : Screen
    data object Session : Screen
    data class Result(val resultId: String) : Screen
    data object Random : Screen
    data object Bank : Screen
    data class Editor(val questionId: String?) : Screen
    data object Transfer : Screen
    data object History : Screen
    data object Account : Screen
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val report: SyncReport) : SyncState
    data class Failed(val message: String) : SyncState
}

/** Local edits are uploaded this long after the last change, so bursts of edits sync once. */
private const val AUTO_SYNC_DELAY_MS = 3_000L

class AppViewModel(
    val repo: Repository = Repository(),
    val cloud: CloudBackend = createCloudBackend(),
) : ViewModel() {
    private val syncEngine = SyncEngine(repo, cloud)

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()

    var session by mutableStateOf<PracticeSession?>(null)
        private set

    var syncState by mutableStateOf<SyncState>(SyncState.Idle)
        private set
    private var syncAgain = false

    /** Whether the signed-in user may edit the shared bank (they have an `admins/{uid}` document). */
    var isAdmin by mutableStateOf(false)
        private set

    /** Result of the last shared-bank operation (publish, edit, delete), for the bank screen. */
    var bankMessage by mutableStateOf<Pair<Boolean, String>?>(null)
        private set

    init {
        viewModelScope.launch {
            // Sync at startup (the shared bank needs no account), and again right after signing in or out.
            cloud.user.distinctUntilChangedBy { it?.uid }.collect { user ->
                isAdmin = user != null && runCatching { cloud.isAdmin() }.getOrDefault(false)
                syncNow()
            }
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            snapshotFlow { repo.version }.drop(1).debounce(AUTO_SYNC_DELAY_MS).collect {
                if (cloud.user.value != null) syncNow()
            }
        }
    }

    /** Fetches shared-bank changes, then (when signed in) syncs the user's own data. */
    fun syncNow() {
        if (syncState == SyncState.Running) {
            syncAgain = true
            return
        }
        viewModelScope.launch {
            do {
                syncAgain = false
                syncState = SyncState.Running
                syncState = try {
                    val sharedChanges = syncEngine.syncShared()
                    if (cloud.user.value != null) {
                        val report = syncEngine.sync(::nowMillis)
                        SyncState.Done(report.copy(downloaded = report.downloaded + sharedChanges))
                    } else {
                        SyncState.Done(SyncReport(uploaded = 0, downloaded = sharedChanges, finishedAt = nowMillis()))
                    }
                } catch (e: CloudException) {
                    SyncState.Failed(e.message ?: "Sync failed.")
                } catch (e: Exception) {
                    SyncState.Failed("Sync failed: ${e.message ?: e::class.simpleName}")
                }
            } while (syncAgain)
        }
    }

    // ---- Question edits: own questions locally, shared ones in the shared bank (admins only) ----

    /** Saves [question]: to the shared bank if it's a shared question (admins only), else to the user's own. */
    fun saveQuestion(question: Question) {
        if (!repo.isShared(question.id)) {
            repo.upsertQuestion(question)
            return
        }
        check(isAdmin) { "Only admins can edit the shared bank." }
        sharedOperation("Saved to the shared bank.") { syncEngine.publish(listOf(question), ::nowMillis) }
    }

    fun deleteQuestion(id: String) {
        if (!repo.isShared(id)) {
            repo.deleteQuestion(id)
            return
        }
        check(isAdmin) { "Only admins can edit the shared bank." }
        sharedOperation("Deleted from the shared bank.") { syncEngine.unpublish(setOf(id), ::nowMillis) }
    }

    /** Admin only: moves the user's own questions [ids] into the shared bank. */
    fun publishOwn(ids: Set<String>) {
        check(isAdmin) { "Only admins can edit the shared bank." }
        val questions = repo.ownQuestions().filter { it.id in ids }
        sharedOperation("Published ${questions.size} question${if (questions.size == 1) "" else "s"} to the shared bank.") {
            syncEngine.publish(questions, ::nowMillis)
        }
    }

    /**
     * Imports a JSON array. Questions whose id is in the shared bank update it when the user is an
     * admin; for everyone else they're skipped. Returns a message for the user.
     */
    fun importJson(text: String): String {
        val summary = repo.importQuestionsJson(text)
        val parts = mutableListOf("Imported ${summary.imported} question${if (summary.imported == 1) "" else "s"}.")
        if (summary.skippedDuplicates > 0) parts += "Skipped ${summary.skippedDuplicates} already in your bank."
        if (summary.shared.isNotEmpty()) {
            if (isAdmin) {
                parts += "Updating ${summary.shared.size} in the shared bank…"
                sharedOperation("Updated ${summary.shared.size} shared question${if (summary.shared.size == 1) "" else "s"}.") {
                    syncEngine.publish(summary.shared, ::nowMillis)
                }
            } else {
                parts += "Skipped ${summary.shared.size} that belong to the shared bank (only admins can change those)."
            }
        }
        return parts.joinToString(" ")
    }

    private fun sharedOperation(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            bankMessage = try {
                block()
                true to success
            } catch (e: CloudException) {
                false to (e.message ?: "The shared bank couldn't be updated.")
            } catch (e: Exception) {
                false to "The shared bank couldn't be updated: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun startSession(config: PracticeConfig) {
        session = PracticeSession(config)
        // Starting from a setup or result screen replaces it, so "back" from the session goes home.
        if (screen is Screen.Setup || screen is Screen.Result) backStack.removeAt(backStack.lastIndex)
        backStack.add(Screen.Session)
    }

    /** Saves the result and replaces the session screen with its review. */
    fun finishSession(timedOut: Boolean = false) {
        val s = session ?: return
        val result = s.finish(timedOut)
        repo.addResult(result)
        session = null
        backStack.removeAll { it == Screen.Session }
        backStack.add(Screen.Result(result.id))
    }

    fun abandonSession() {
        session = null
        backStack.removeAll { it == Screen.Session }
    }
}
