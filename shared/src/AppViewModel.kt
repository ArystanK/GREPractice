package kz.arctan.grepractice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kz.arctan.grepractice.data.Repository
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
}

class AppViewModel : ViewModel() {
    val repo = Repository()

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()

    var session by mutableStateOf<PracticeSession?>(null)
        private set

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
