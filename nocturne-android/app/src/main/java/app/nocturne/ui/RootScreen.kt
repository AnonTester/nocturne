package app.nocturne.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.nocturne.MainViewModel
import app.nocturne.data.AppMode
import app.nocturne.playback.EngineEvent
import app.nocturne.ui.theme.Nocturne
import app.nocturne.ui.theme.NocturneTheme

private enum class Dlg { SLEEP, ALBUMS, LIBRARY, SETTINGS, APPS }

@UnstableApi
@Composable
fun RootScreen(
    vm: MainViewModel,
    homeSignal: Int,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onPickFolder: () -> Unit,
    onApplyWindow: (keepAwake: Boolean, fullscreen: Boolean, brightness: Float) -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val player by vm.player.collectAsState()
    val lib by vm.libraryStatus.collectAsState()
    val trackCount by vm.trackCount.collectAsState()

    NocturneTheme(settings.theme) {
        CompositionLocalProvider(LocalFullscreen provides settings.fullscreen) {
            val c = Nocturne.colors
            var dialog by remember { mutableStateOf<Dlg?>(null) }
            var settingsTab by remember { mutableStateOf(SettingsTab.THEME) }
            var dimmed by remember { mutableStateOf(false) }

            // A home screen never exits on Back: it just wakes from standby (dialogs handle their own Back).
            androidx.activity.compose.BackHandler(enabled = dialog == null) { dimmed = false }

            // HOME pressed: act like a launcher and return to the main screen.
            LaunchedEffect(homeSignal) { if (homeSignal > 0) { dialog = null; dimmed = false } }
            LaunchedEffect(Unit) { vm.events.collect { if (it is EngineEvent.SleepFinished) dimmed = true } }
            // Dimmed screen also drops the panel brightness (matters most on AMOLED).
            LaunchedEffect(settings.keepAwake, settings.fullscreen, dimmed) {
                onApplyWindow(settings.keepAwake, settings.fullscreen, if (dimmed) 0.01f else -1f)
            }

            Box(Modifier.fillMaxSize().background(c.background)) {
                // Fullscreen hides the bars; older Android versions still report their insets, so only keep clear of the cutout then.
                val insets = if (settings.fullscreen) WindowInsets.displayCutout else WindowInsets.safeDrawing
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                    val wide = maxWidth >= 600.dp
                    Column(Modifier.fillMaxSize()) {
                        Header(
                            mode = settings.mode, wide = wide, sleep = player.sleep, dimmed = dimmed,
                            onMode = vm::setMode, onSleep = { dialog = Dlg.SLEEP },
                            onDim = { dimmed = !dimmed }, onSettings = { settingsTab = SettingsTab.THEME; dialog = Dlg.SETTINGS },
                        )
                        AnimatedVisibility(lib.newTracksFound > 0) {
                            Row(
                                Modifier.fillMaxWidth().background(c.accentContainer).padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Refresh, null, tint = c.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("${lib.newTracksFound} new tracks found on the Samba share", color = c.onAccentContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconTile(Icons.Rounded.Close, "Dismiss", vm::dismissNewTracks, size = 44.dp, container = c.accentContainer)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                settings.mode == AppMode.AUDIOBOOKSHELF ->
                                    AudiobookshelfStage(vm, settings.abs) { settingsTab = SettingsTab.AUDIOBOOKS; dialog = Dlg.SETTINGS }
                                trackCount == 0 && player.track == null ->
                                    EmptyLibrary(
                                        scanning = lib.scanning, message = lib.message, hasPermission = hasAudioPermission,
                                        onGrant = onRequestPermissions,
                                        onSettings = { settingsTab = SettingsTab.LIBRARY; dialog = Dlg.SETTINGS },
                                    )
                                else -> MusicStage(vm, onAlbums = { dialog = Dlg.ALBUMS }, onLibrary = { dialog = Dlg.LIBRARY })
                            }
                        }
                    }
                }

                if (dimmed) {
                    DimOverlay(
                        title = if (settings.mode == AppMode.AUDIOBOOKSHELF) "Audiobookshelf" else player.track?.title.orEmpty(),
                        artist = if (settings.mode == AppMode.AUDIOBOOKSHELF) "" else player.track?.artist.orEmpty(),
                        onWake = { dimmed = false },
                    )
                }
            }

            when (dialog) {
                Dlg.SLEEP -> SleepTimerDialog(player.sleep, { dialog = null }, vm::startSleepTimer, vm::cancelSleepTimer, vm::extendSleepTimer)
                Dlg.ALBUMS -> AlbumsDialog(vm) { dialog = null }
                Dlg.LIBRARY -> TracksDialog(vm, player.track?.id) { dialog = null }
                Dlg.SETTINGS -> SettingsDialog(
                    vm, settings, settingsTab, hasAudioPermission, homeSignal, onRequestPermissions, onPickFolder,
                    onOpenApps = { dialog = Dlg.APPS }, onDismiss = { dialog = null },
                )
                Dlg.APPS -> AppsDialog { dialog = null }
                null -> {}
            }
        }
    }
}

@Composable
private fun EmptyLibrary(scanning: Boolean, message: String?, hasPermission: Boolean, onGrant: () -> Unit, onSettings: () -> Unit) {
    val c = Nocturne.colors
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Panel(Modifier.widthLimit()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.LibraryMusic, null, tint = c.accent, modifier = Modifier.size(48.dp))
                Text("Your library is empty", color = c.textPrimary, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Add music from this device or connect a Samba share to start listening.",
                    color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
                if (scanning) Text(message ?: "Scanning...", color = c.accent, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!hasPermission) PrimaryButton("Grant music access", onGrant, icon = Icons.Rounded.Check)
                    SecondaryButton("Library settings", onSettings, icon = Icons.Rounded.Settings)
                }
            }
        }
    }
}

private fun Modifier.widthLimit(): Modifier = this.then(Modifier.width(560.dp))
