package kz.arctan.grepractice.ui

import androidx.compose.runtime.Composable

/** Intercepts the system back gesture where the platform has one (Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
