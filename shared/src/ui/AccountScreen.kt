package kz.arctan.grepractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.arctan.grepractice.AppViewModel
import kz.arctan.grepractice.SyncState
import kz.arctan.grepractice.data.cloud.CloudException
import kz.arctan.grepractice.data.formatDateTime

@Composable
fun AccountScreen(vm: AppViewModel) {
    val user by vm.cloud.user.collectAsState()
    ScreenScaffold(title = "Account & sync", onBack = vm::back) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val u = user
            when {
                u == null -> SignInCard(vm, guest = false)
                u.isAnonymous -> {
                    SyncCard(vm, "Guest account", guest = true)
                    SignInCard(vm, guest = true)
                }
                else -> SyncCard(vm, u.email ?: "Signed in", guest = false)
            }
        }
    }
}

@Composable
private fun SignInCard(vm: AppViewModel, guest: Boolean) {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    // Not saveable: the password stays out of saved instance state, and an in-flight sign-in is cancelled with the screen.
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun run(action: suspend () -> Unit, success: String? = null) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            message = try {
                action()
                success?.let { true to it }
            } catch (e: CloudException) {
                false to (e.message ?: "Something went wrong.")
            } catch (e: Exception) {
                false to "Something went wrong: ${e.message}"
            }
            busy = false
        }
    }

    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !busy
    SectionCard(title = if (guest) "Use your data on other devices" else "Sign in to sync") {
        Text(
            if (guest) {
                "Sign in with Google or email to reach your data from your other devices. " +
                    "Everything on this device is merged into that account."
            } else {
                "Keep your question bank and practice history in sync between your devices. " +
                    "Signing in merges what's on this device into your account."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (vm.cloud.googleSignInAvailable) {
            Button(enabled = !busy, onClick = { run({ vm.cloud.signInWithGoogle() }) }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue with Google")
            }
            Text(
                "or use email",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = canSubmit, onClick = { run({ vm.cloud.signIn(email, password) }) }) { Text("Sign in") }
            OutlinedButton(enabled = canSubmit, onClick = { run({ vm.cloud.signUp(email, password) }) }) { Text("Create account") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        TextButton(
            enabled = email.isNotBlank() && !busy,
            onClick = { run({ vm.cloud.sendPasswordReset(email) }, success = "Password reset email sent to $email.") },
        ) { Text("Forgot password?") }
        message?.let { (ok, text) ->
            Text(text, color = if (ok) LocalFeedbackColors.current.correct else MaterialTheme.colorScheme.error)
        }
    }

    if (!guest) {
        SectionCard(title = "No account?") {
            Text(
                "Continue as a guest to back up this device's data to the cloud without signing up. " +
                    "A guest account only works on this device; you can sign in with Google or email later.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(enabled = !busy, onClick = { run({ vm.cloud.signInAnonymously() }) }) { Text("Continue as guest") }
        }
    }
}

@Composable
private fun SyncCard(vm: AppViewModel, account: String, guest: Boolean) {
    val scope = rememberCoroutineScope()
    val fb = LocalFeedbackColors.current
    var confirmGuestSignOut by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "Signed in") {
        Text(account, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (vm.isAdmin) {
            Text(
                "Admin: you can publish and edit questions in the shared bank.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Changes sync automatically a few seconds after you make them, and whenever the app starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val state = vm.syncState) {
                SyncState.Idle -> Text("Not synced yet.")
                SyncState.Running -> {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Syncing…")
                }
                is SyncState.Done -> Text(
                    "Synced ${formatDateTime(state.report.finishedAt)} · ${state.report.uploaded} uploaded, ${state.report.downloaded} downloaded",
                    color = fb.correct,
                )
                is SyncState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::syncNow, enabled = vm.syncState != SyncState.Running) { Text("Sync now") }
            OutlinedButton(onClick = { if (guest) confirmGuestSignOut = true else scope.launch { vm.cloud.signOut() } }) { Text("Sign out") }
        }
        Text(
            if (guest) "Guest data is backed up for this device only." else "Signing out keeps your data on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirmGuestSignOut) {
        ConfirmDialog(
            title = "Sign out of the guest account?",
            text = "Your data stays on this device, but the guest account's cloud backup can't be reached again after signing out.",
            confirmLabel = "Sign out",
            onConfirm = { confirmGuestSignOut = false; scope.launch { vm.cloud.signOut() } },
            onDismiss = { confirmGuestSignOut = false },
        )
    }
}
