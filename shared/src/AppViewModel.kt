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
import kz.arctan.grepractice.data.cloud.createCloudBackend
import kz.arctan.grepractice.data.nowMillis
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.PracticeSession

sealed interface Screen {
    data object Home : Screen
    /** [exam] = full simulated test; otherwise custom/topic practice, optionally preselecting [topic]. */
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

    init {
        viewModelScope.launch {
            // Sync at startup when already signed in, and right after signing in.
            cloud.user.distinctUntilChangedBy { it?.uid }.collect { user ->
                if (user != null) syncNow() else syncState = SyncState.Idle
            }
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            snapshotFlow { repo.version }.drop(1).debounce(AUTO_SYNC_DELAY_MS).collect {
                if (cloud.user.value != null) syncNow()
            }
        }
    }

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
                    SyncState.Done(syncEngine.sync(::nowMillis))
                } catch (e: CloudException) {
                    SyncState.Failed(e.message ?: "Sync failed.")
                } catch (e: Exception) {
                    SyncState.Failed("Sync failed: ${e.message ?: e::class.simpleName}")
                }
            } while (syncAgain)
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
