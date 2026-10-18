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

    FullDialog(onDismiss, "Bedside Sleep Timer", "Drift off to your music", Icons.Rounded.Bedtime, maxWidth = 640.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (sleep.isActive) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentContainer)
                        .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionLabel(if (sleep.waitingForTrackToEnd) "Finishing current song" else "Active sleep timer")
                    Text(
                        if (sleep.waitingForTrackToEnd) "Waiting for song to end..." else "%d:%02d".format(sleep.remainingSeconds / 60, sleep.remainingSeconds % 60),
                        color = if (sleep.waitingForTrackToEnd) c.secondary else c.onAccentContainer,
                        fontSize = if (sleep.waitingForTrackToEnd) 20.sp else 40.sp,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                    )
                    Text(
                        if (sleep.finishCurrentTrack) "Will finish the current song instead of stopping abruptly" else "Stops right when the time is up",
                        color = c.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("+15m", { onExtend(15) }, icon = Icons.Rounded.Add)
                        SecondaryButton("+30m", { onExtend(30) }, icon = Icons.Rounded.Add)
                        SecondaryButton("Cancel", onCancel, icon = Icons.Rounded.Close, tint = c.danger)
                    }
                }
            }

            SectionLabel(if (sleep.isActive) "Change or re-arm" else "Select duration")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((mins, caption) in listOf(30 to "Quick rest", 60 to "1 hour", 90 to "1.5 hours")) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                            .border(1.dp, c.border, RoundedCornerShape(16.dp))
                            .clickable { onStart(mins, finishTrack); onDismiss() }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Schedule, null, tint = c.accent, modifier = Modifier.size(26.dp))
                        Text("${mins}m", color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(caption, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NocturneTextField(
                    custom, { custom = it.filter(Char::isDigit).take(3) }, "Custom minutes",
                    Modifier.width(150.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val mins = custom.toIntOrNull()
                PrimaryButton("Start ${custom.ifEmpty { "?" }} min timer", { if (mins != null && mins > 0) { onStart(mins, finishTrack); onDismiss() } }, Modifier.weight(1f), enabled = mins != null && mins > 0)
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable { finishTrack = !finishTrack }.padding(14.dp),
                verticalAlignment = Alignment.Top,
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
                        "When the timer expires the song plays to its end, then playback stops. Never cuts you off mid-verse.",
                        color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
