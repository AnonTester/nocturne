package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.playback.SleepTimerState
import app.nocturne.ui.theme.Nocturne

@Composable
fun SleepTimerDialog(
    sleep: SleepTimerState,
    onDismiss: () -> Unit,
    onStart: (minutes: Int, finishCurrent: Boolean) -> Unit,
    onCancel: () -> Unit,
    onExtend: (Int) -> Unit,
) {
    val c = Nocturne.colors
    var custom by remember { mutableStateOf("45") }
    var finishTrack by remember { mutableStateOf(sleep.finishCurrentTrack) }

    FullDialog(onDismiss, "Sleep Timer", "Drift off to your music", Icons.Rounded.Bedtime, maxWidth = 640.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sleep.isActive) ActiveTimerCard(sleep, onExtend, onCancel)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (mins in listOf(30, 60, 90)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                            .border(1.dp, c.border, RoundedCornerShape(16.dp))
                            .clickable { onStart(mins, finishTrack); onDismiss() }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Schedule, null, tint = c.accent, modifier = Modifier.size(26.dp))
                        Text(if (mins == 90) "1.5 h" else if (mins == 60) "1 h" else "${mins}m", color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NocturneTextField(
                    custom, { custom = it.filter(Char::isDigit).take(3) }, "Custom minutes",
                    Modifier.width(150.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val mins = custom.toIntOrNull()
                PrimaryButton("Start ${custom.ifEmpty { "?" }} min", { if (mins != null && mins > 0) { onStart(mins, finishTrack); onDismiss() } }, Modifier.weight(1f), enabled = mins != null && mins > 0)
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable { finishTrack = !finishTrack }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (finishTrack) c.accent else c.surface)
                        .border(1.dp, if (finishTrack) c.accent else c.border, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (finishTrack) Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Finish current song before stopping", color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The song plays to its end, then playback stops.",
                        color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Countdown with its +15m/+30m/Cancel actions; side by side on a wide (landscape) screen, stacked in portrait. */
@Composable
private fun ActiveTimerCard(sleep: SleepTimerState, onExtend: (Int) -> Unit, onCancel: () -> Unit) {
    val c = Nocturne.colors
    val sideBySide = !isNarrowWindow()
    val shape = RoundedCornerShape(18.dp)
    val countdown = if (sleep.waitingForTrackToEnd) "Waiting for song to end..." else "%d:%02d".format(sleep.remainingSeconds / 60, sleep.remainingSeconds % 60)
    val note = if (sleep.finishCurrentTrack) "Finishes the current song first" else "Stops when the time is up"
    val actions: @Composable (Modifier) -> Unit = { m ->
        // Icons are dropped in the stacked (portrait) layout so "Cancel" is not cut off.
        val icons = sideBySide
        SecondaryButton("+15m", { onExtend(15) }, m, icon = if (icons) Icons.Rounded.Add else null)
        SecondaryButton("+30m", { onExtend(30) }, m, icon = if (icons) Icons.Rounded.Add else null)
        SecondaryButton("Cancel", onCancel, m, icon = if (icons) Icons.Rounded.Close else null, tint = c.danger)
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.accentContainer).border(1.dp, c.accent.copy(alpha = 0.4f), shape).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(if (sleep.waitingForTrackToEnd) "Finishing current song" else "Active sleep timer")
                Text(
                    countdown,
                    color = if (sleep.waitingForTrackToEnd) c.secondary else c.onAccentContainer,
                    fontSize = if (sleep.waitingForTrackToEnd) 20.sp else 40.sp,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                )
                Text(note, color = c.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            if (sideBySide) {
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions(Modifier) }
            }
        }
        if (!sideBySide) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions(Modifier.weight(1f)) }
        }
    }
}
