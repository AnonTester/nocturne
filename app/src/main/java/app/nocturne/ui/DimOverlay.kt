package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.ui.theme.Nocturne
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

/** How often the standby content fades out and reappears elsewhere, against burn-in. */
private const val MOVE_INTERVAL_MS = 5 * 60 * 1000L
private const val FADE_MS = 900

/**
 * Nearly-black standby screen; tap anywhere to wake. Shows the time and what is playing.
 * It starts centred and every few minutes fades out and back in at another spot (burn-in protection).
 * The spot is a fraction of the free space around the content, so it always stays fully on screen.
 */
@Composable
fun DimOverlay(title: String, artist: String, onWake: () -> Unit) {
    val c = Nocturne.colors
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(1000) } }
    var spot by remember { mutableStateOf(0f to 0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MOVE_INTERVAL_MS)
            alpha.animateTo(0f, tween(FADE_MS))
            val old = spot
            var next = old
            repeat(20) {
                if (abs(next.first - old.first) + abs(next.second - old.second) < 0.8f) next = Random.nextFloat() * 2 - 1 to Random.nextFloat() * 2 - 1
            }
            spot = next
            alpha.animateTo(1f, tween(FADE_MS))
        }
    }
    Box(
        Modifier.fillMaxSize().background(c.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake)
            .padding(24.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boxW = constraints.maxWidth
            val boxH = constraints.maxHeight
            var content by remember { mutableStateOf(IntSize.Zero) }
            Column(
                Modifier.align(Alignment.Center)
                    .widthIn(max = maxWidth * 0.8f)
                    .onSizeChanged { content = it }
                    .offset {
                        val freeX = ((boxW - content.width) / 2f).coerceAtLeast(0f)
                        val freeY = ((boxH - content.height) / 2f).coerceAtLeast(0f)
                        IntOffset((spot.first * freeX).roundToInt(), (spot.second * freeY).roundToInt())
                    }
                    .graphicsLayer { this.alpha = alpha.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Standby • tap anywhere to wake", color = c.textMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = c.textSecondary.copy(alpha = 0.7f), fontSize = 64.sp, fontFamily = FontFamily.Monospace)
                Text(title, color = c.textMuted, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(artist, color = c.textMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
