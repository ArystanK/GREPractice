package kz.arctan.grepractice

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import kz.arctan.grepractice.ui.AccountScreen
import kz.arctan.grepractice.ui.BankScreen
import kz.arctan.grepractice.ui.EditorScreen
import kz.arctan.grepractice.ui.ExamsScreen
import kz.arctan.grepractice.ui.GreTheme
import kz.arctan.grepractice.ui.HistoryScreen
import kz.arctan.grepractice.ui.HomeScreen
import kz.arctan.grepractice.ui.PlatformBackHandler
import kz.arctan.grepractice.ui.RandomScreen
import kz.arctan.grepractice.ui.ResultScreen
import kz.arctan.grepractice.ui.SessionScreen
import kz.arctan.grepractice.ui.SetupScreen
import kz.arctan.grepractice.ui.TopicHistoryScreen
import kz.arctan.grepractice.ui.TransferScreen

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    // The saved-state handle restores the back stack after Android kills the process in the background.
    val vm = viewModel { AppViewModel(savedState = createSavedStateHandle()) }
    GreTheme {
        Surface {
            // The session screen handles back itself (it asks before quitting).
            PlatformBackHandler(enabled = vm.backStack.size > 1 && vm.screen != Screen.Session) { vm.back() }
            when (val screen = vm.screen) {
                Screen.Home -> HomeScreen(vm)
                is Screen.Setup -> SetupScreen(vm, screen)
                Screen.Session -> SessionScreen(vm)
                is Screen.Result -> ResultScreen(vm, screen.resultId)
                Screen.Random -> RandomScreen(vm)
                Screen.Bank -> BankScreen(vm)
                is Screen.Editor -> EditorScreen(vm, screen.questionId)
                Screen.Transfer -> TransferScreen(vm)
                is Screen.History -> HistoryScreen(vm, screen.byTopic)
                is Screen.TopicHistory -> TopicHistoryScreen(vm, screen.topic)
                Screen.Account -> AccountScreen(vm)
                Screen.Exams -> ExamsScreen(vm)
            }
        }
    }
}
