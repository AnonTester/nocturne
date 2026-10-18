package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.ui.theme.Nocturne
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Nearly-black standby screen; tap anywhere to wake. Shows the time and what is playing. */
@Composable
fun DimOverlay(title: String, artist: String, onWake: () -> Unit) {
    val c = Nocturne.colors
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(1000) } }
    Box(
        Modifier.fillMaxSize().background(c.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake)
            .padding(24.dp),
    ) {
        Text("Standby • tap anywhere to wake", color = c.textMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = c.textSecondary.copy(alpha = 0.7f), fontSize = 64.sp, fontFamily = FontFamily.Monospace)
            Text(title, color = c.textMuted, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(artist, color = c.textMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
