package kz.arctan.grepractice.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E0F9),
    tertiary = Color(0xFF77536D),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE3E1EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF2A3C9E),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF434659),
    tertiary = Color(0xFFE6BAD7),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF46464F),
)

/** Correct / incorrect feedback colors, which Material's scheme doesn't provide. */
@Immutable
data class FeedbackColors(
    val correct: Color,
    val correctContainer: Color,
    val incorrect: Color,
    val incorrectContainer: Color,
    val flag: Color,
)

private val LightFeedback = FeedbackColors(
    correct = Color(0xFF1B7F3B),
    correctContainer = Color(0xFFD3F5DC),
    incorrect = Color(0xFFB3261E),
    incorrectContainer = Color(0xFFFADAD7),
    flag = Color(0xFFB26A00),
)

private val DarkFeedback = FeedbackColors(
    correct = Color(0xFF7DDC95),
    correctContainer = Color(0xFF0F3D1E),
    incorrect = Color(0xFFFFB4AB),
    incorrectContainer = Color(0xFF5C1712),
    flag = Color(0xFFFFB95C),
)

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

@Composable
fun GreTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalFeedbackColors provides if (dark) DarkFeedback else LightFeedback,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
