package app.nocturne.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.MainViewModel
import app.nocturne.data.AppMode
import app.nocturne.data.RepeatMode
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.playback.PlayStatus
import app.nocturne.playback.PlayerState
import app.nocturne.playback.SleepTimerState
import app.nocturne.playback.VolumeCurve
import app.nocturne.ui.theme.Nocturne
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ header

@Composable
fun Header(
    mode: AppMode,
    wide: Boolean,
    sleep: SleepTimerState,
    dimmed: Boolean,
    onMode: (AppMode) -> Unit,
    onSleep: () -> Unit,
    onDim: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Nocturne.colors
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(c.surface).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mode switch (either music or audiobooks, never both)
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ModeChip("Music", Icons.Rounded.Album, mode == AppMode.MUSIC, wide, c.accent, c.onAccent) { onMode(AppMode.MUSIC) }
            ModeChip("Audiobookshelf", Icons.Rounded.MenuBook, mode == AppMode.AUDIOBOOKSHELF, wide, c.secondary, c.onSecondary) { onMode(AppMode.AUDIOBOOKSHELF) }
        }
        Spacer(Modifier.weight(1f))
        Clock(wide = wide, onClick = onDim)
        Spacer(Modifier.width(if (wide) 10.dp else 6.dp))
        SleepPill(sleep, wide, onSleep)
        Spacer(Modifier.weight(1f))
        IconTile(Icons.Rounded.Visibility, if (dimmed) "Wake screen" else "Dim screen", onDim, selected = dimmed, size = 44.dp, iconSize = 22.dp)
        Spacer(Modifier.width(if (wide) 8.dp else 6.dp))
        IconTile(Icons.Rounded.Settings, "Settings", onSettings, size = 44.dp, iconSize = 22.dp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

@Composable
private fun ModeChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, showLabel: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    val c = Nocturne.colors
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(13.dp))
            .background(if (selected) bg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (selected) fg else c.textSecondary, modifier = Modifier.size(20.dp))
        if (showLabel) {
            Spacer(Modifier.width(7.dp))
            Text(label, color = if (selected) fg else c.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun Clock(wide: Boolean, onClick: () -> Unit) {
    val c = Nocturne.colors
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000) }
    }
    Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(now.format(HM), color = c.textPrimary, fontSize = if (wide) 30.sp else 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (wide) {
            Text(now.format(SS), color = c.textMuted, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            

            Text(now.format(DATE), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        }
    }
}

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SS: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
private fun SleepPill(sleep: SleepTimerState, wide: Boolean, onClick: () -> Unit) {
    val c = Nocturne.colors
    val active = sleep.isActive
    val bg = if (active) (if (sleep.waitingForTrackToEnd) c.secondary.copy(alpha = 0.18f) else c.accentContainer) else c.surfaceHigh
    val fg = if (active) (if (sleep.waitingForTrackToEnd) c.secondary else c.accent) else c.textSecondary
    val text = when {
        !active -> "Sleep"
        sleep.waitingForTrackToEnd -> "Ending..."
        else -> "%d:%02d".format(sleep.remainingSeconds / 60, sleep.remainingSeconds % 60)
    }
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, fg.copy(alpha = if (active) 0.5f else 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = if (wide) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
    }
}

// ------------------------------------------------------------------ music stage

@UnstableApi
@Composable
fun MusicStage(
    vm: MainViewModel,
    onAlbums: () -> Unit,
    onLibrary: () -> Unit,
) {
    val player by vm.player.collectAsState()
    val trackCount by vm.trackCount.collectAsState()
    val c = Nocturne.colors

    BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel(Modifier.weight(0.38f).fillMaxHeight()) {
                    NowPlayingColumn(vm, player, onAlbums, onLibrary, coverFill = true, modifier = Modifier.fillMaxSize().padding(10.dp))
                }
                Column(Modifier.weight(0.62f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Panel(Modifier.weight(1f).fillMaxWidth()) {
                        Transport(vm, player, compact = false, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    VolumePanel(vm, player)
                    StatusStrip(player, trackCount, vm)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel(Modifier.weight(1f).fillMaxWidth()) {
                    NowPlayingColumn(vm, player, onAlbums, onLibrary, coverFill = true, modifier = Modifier.fillMaxSize().padding(12.dp))
                }
                Panel(Modifier.fillMaxWidth()) {
                    Transport(vm, player, compact = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
                }
                VolumePanel(vm, player)
                StatusStrip(player, trackCount, vm)
            }
        }
    }
    // keep colour tokens referenced for tooling
    @Suppress("UNUSED_VARIABLE") val unused = c
}

@UnstableApi
@Composable
private fun NowPlayingColumn(
    vm: MainViewModel,
    player: PlayerState,
    onAlbums: () -> Unit,
    onLibrary: () -> Unit,
    coverFill: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Nocturne.colors
    val track = player.track
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track?.id) {
        cover = null
        cover = track?.let { vm.cover(it) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CoverArt(track, cover, Modifier.aspectRatio(1f).fillMaxSize(), fill = coverFill)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            track?.title ?: "No track selected",
            color = c.textPrimary, style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            track?.artist ?: "Pick something to play",
            color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            track?.album ?: "Nocturne",
            color = c.textMuted, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Albums", onAlbums, Modifier.weight(1f), icon = Icons.Rounded.FolderOpen)
            SecondaryButton("Library", onLibrary, Modifier.weight(1f), icon = Icons.Rounded.LibraryMusic)
        }
    }
}

/** Embedded art if present, otherwise a themed generated disc (never a missing-image hole). */
@Composable
fun CoverArt(track: Track?, bitmap: Bitmap?, modifier: Modifier = Modifier, fill: Boolean = true) {
    val c = Nocturne.colors
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(modifier.clip(shape).border(1.dp, c.border, shape), contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight)
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), track?.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                colorFilter = nightTint(c),
            )
        } else {
            val tint = remember(track?.id) { hashToFraction(track?.id ?: "") }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(lerp(c.coverStart, c.accentContainer, tint), c.coverEnd)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(d * 0.78f).border(2.dp, c.accent.copy(alpha = 0.25f), CircleShape))
                Box(Modifier.size(d * 0.58f).border(1.dp, c.accent.copy(alpha = 0.4f), CircleShape))
                Box(Modifier.size(d * 0.34f).background(c.accent.copy(alpha = 0.85f), CircleShape))
                Box(Modifier.size(d * 0.09f).background(c.coverEnd, CircleShape))
            }
        }
        if (track != null) {
            val label = if (track.source == Source.SAMBA) "SAMBA" else "LOCAL"
            Text(
                label, color = c.textPrimary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(c.scrim, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Warm Amber and Night Red avoid blue light on purpose, so artwork is rendered as a monochrome in the theme's accent. */
@Composable
internal fun nightTint(c: app.nocturne.ui.theme.NocturneColors): androidx.compose.ui.graphics.ColorFilter? {
    if (c.id != app.nocturne.ui.theme.ThemeId.AMBER && c.id != app.nocturne.ui.theme.ThemeId.RED) return null
    val a = c.accent
    val peak = maxOf(a.red, a.green, a.blue)
    val (r, g, b) = Triple(a.red / peak, a.green / peak, a.blue / peak)
    // luminance (0.299/0.587/0.114) mapped onto the accent colour
    val m = androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
            0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
            0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(m)
}

private fun hashToFraction(s: String): Float = ((s.hashCode() and 0x7fffffff) % 1000) / 1000f

private fun lerp(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t * 0.6f)

// ------------------------------------------------------------------ transport

@UnstableApi
@Composable
private fun Transport(vm: MainViewModel, player: PlayerState, compact: Boolean, modifier: Modifier = Modifier) {
    val c = Nocturne.colors
    val position by vm.position.collectAsState()
    val isPlaying = player.status == PlayStatus.PLAYING
    val duration = player.durationMs
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) (dragValue * duration).toLong() else position
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(modifier, verticalArrangement = Arrangement.Center) {
        Slider(
            value = fraction,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo((dragValue * duration).toLong()); dragging = false },
            enabled = duration > 0,
            colors = sliderColors(),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown), color = c.textSecondary, style = MaterialTheme.typography.labelSmall)
            Text(
                if (duration > 0) "-" + formatTime(duration - shown) else "--:--",
                color = c.textMuted, style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        val small = if (compact) 44.dp else 52.dp
        val mid = if (compact) 48.dp else 56.dp
        val big = if (compact) 64.dp else 72.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Shuffle, "Shuffle", vm::toggleShuffle, size = small, selected = player.shuffle)
            JumpButton(back = true, size = small) { vm.jump(-15_000) }
            IconTile(Icons.Rounded.SkipPrevious, "Previous", vm::previous, size = mid, iconSize = 28.dp, tint = c.textPrimary)
            Box(
                Modifier.size(big).clip(RoundedCornerShape(22.dp)).background(c.accent).clickable { vm.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (player.status == PlayStatus.LOADING) {
                    androidx.compose.material3.CircularProgressIndicator(color = c.onAccent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                } else {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = c.onAccent, modifier = Modifier.size(40.dp))
                }
            }
            IconTile(Icons.Rounded.SkipNext, "Next", { vm.next() }, size = mid, iconSize = 28.dp, tint = c.textPrimary)
            JumpButton(back = false, size = small) { vm.jump(15_000) }
            IconTile(
                if (player.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat ${player.repeat.name.lowercase()}", vm::cycleRepeat, size = small, selected = player.repeat != RepeatMode.OFF,
            )
        }
        if (player.error != null) {
            Spacer(Modifier.height(6.dp))
            Text(player.error, color = c.danger, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun JumpButton(back: Boolean, size: Dp, onClick: () -> Unit) {
    val c = Nocturne.colors
    Box(
        Modifier.size(size).clip(ControlShape).background(c.surfaceHigh).border(1.dp, c.border, ControlShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (back) Icons.Rounded.RotateLeft else Icons.Rounded.RotateRight, if (back) "Back 15 seconds" else "Forward 15 seconds", tint = c.textSecondary, modifier = Modifier.size(size * 0.58f))
        Text("15", color = c.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ volume

@UnstableApi
@Composable
private fun VolumePanel(vm: MainViewModel, p: PlayerState) {
    val c = Nocturne.colors
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    if (p.muted) Icons.Rounded.VolumeOff else if (p.volume < 25f) Icons.Rounded.VolumeDown else Icons.Rounded.VolumeUp,
                    if (p.muted) "Unmute" else "Mute", vm::toggleMute, size = 40.dp, iconSize = 22.dp,
                    selected = p.muted, selectedContainer = c.danger.copy(alpha = 0.18f), selectedTint = c.danger,
                )
                Spacer(Modifier.width(10.dp))
                Text(if (p.muted) "Muted" else "%.1f%%".format(p.volume), color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    "(" + (if (p.muted) "-∞" else VolumeCurve.decibels(p.volume, p.whisper).toString()) + " dB)",
                    color = c.textMuted, style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                val wBg = if (p.whisper) c.accentContainer else c.surfaceHigh
                val wFg = if (p.whisper) c.accent else c.textSecondary
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(wBg)
                        .border(1.dp, wFg.copy(alpha = if (p.whisper) 0.5f else 0.25f), RoundedCornerShape(12.dp))
                        .clickable { vm.toggleWhisper() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = wFg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Whisper", color = wFg, style = MaterialTheme.typography.labelLarge)
                }
            }
            Slider(value = p.volume, onValueChange = vm::setVolume, valueRange = 0f..100f, steps = 199, colors = sliderColors())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, delta) in listOf("-1.0%" to -1f, "-0.5%" to -0.5f, "+0.5%" to 0.5f, "+1.0%" to 1f)) {
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh)
                            .border(1.dp, c.border, RoundedCornerShape(12.dp)).clickable { vm.adjustVolume(delta) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (kotlin.math.abs(delta) < 1f) c.accent else c.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ status strip

@UnstableApi
@Composable
private fun StatusStrip(p: PlayerState, trackCount: Int, vm: MainViewModel) {
    val c = Nocturne.colors
    val pending by vm.pendingMetadata.collectAsState()
    val lib by vm.libraryStatus.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(c.accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        val queue = if (p.scopeName != null) "${p.scopeName} \u2022 ${p.queueSize} tracks" else "${"%,d".format(trackCount)} tracks"
        Text(
            (if (p.shuffle) "Shuffle" else "Sequential") + " \u2022 $queue",
            color = c.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        val right = when {
            lib.scanning -> "Scanning..."
            pending > 0 -> "Reading tags: $pending left"
            p.sleep.isActive -> "Sleep in ${(p.sleep.remainingSeconds + 59) / 60}m"
            else -> "Bedside ready"
        }
        Text(right, color = c.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
