package kz.arctan.grepractice.ui.math

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.arctan.grepractice.data.ImageStore
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * A question figure. Figures from test PDFs are black on white, so they sit on a white card in
 * both themes. A figure that hasn't been downloaded yet shows a placeholder until sync stores it.
 */
@Composable
fun QuestionImage(name: String, alt: String, maxHeight: Dp = 360.dp) {
    val bitmap = remember(name, ImageStore.version) {
        ImageStore.load(name)?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White) {
                Image(
                    bitmap = bitmap,
                    contentDescription = alt.ifBlank { "Figure" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(8.dp).widthIn(max = 560.dp).heightIn(max = maxHeight),
                )
            }
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    "Figure not on this device yet. It downloads when you sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
