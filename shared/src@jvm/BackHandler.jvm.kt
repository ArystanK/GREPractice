package kz.arctan.grepractice.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back; navigation uses the on-screen Back button.
}
