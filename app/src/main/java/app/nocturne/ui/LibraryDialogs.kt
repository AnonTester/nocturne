package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.nocturne.MainViewModel
import app.nocturne.data.FolderRow
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.playback.QueueScope
import app.nocturne.ui.theme.Nocturne

/** Search box with its own text state (a Flow-backed value drops IME input) and a Search key that closes the keyboard. */
@Composable
private fun SearchField(initial: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(initial) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    NocturneTextField(
        text, { text = it; onChange(it) }, placeholder, modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
    )
}

// ------------------------------------------------------------------ albums / folders

@UnstableApi
@Composable
fun AlbumsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = Nocturne.colors
    val query by vm.folderQuery.collectAsState()
    val folders by vm.folders.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<FolderRow?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.folderQuery.value = "" } }
    var folderTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    LaunchedEffect(selected) { folderTracks = selected?.let { vm.tracksIn(it.folderPath) } ?: emptyList() }

    FullDialog(
        onDismiss,
        title = selected?.name ?: "Folders & Albums",
        subtitle = selected?.let { "${folderTracks.size} tracks" } ?: "${folders.size} folders",
        icon = Icons.Rounded.FolderOpen,
    ) {
        val sel = selected
        if (sel == null) {
            Column(Modifier.fillMaxSize()) {
                SearchField(query, { vm.folderQuery.value = it }, "Search folders or artists", Modifier.fillMaxWidth().padding(12.dp))
                if (folders.isEmpty()) EmptyHint("No folders yet. Add music in Settings > Library.")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(folders, key = { it.folderPath + "|" + it.name }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                                .clickable { selected = f }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accentContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Album, null, tint = c.accent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = c.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${f.trackCount} tracks • ${f.sampleArtist}", color = c.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconTile(Icons.Rounded.PlayArrow, "Play folder", { vm.playFolder(f, f.source, false); onDismiss() }, size = 42.dp, iconSize = 22.dp)
                            Spacer(Modifier.width(6.dp))
                            IconTile(Icons.Rounded.Shuffle, "Shuffle folder", { vm.playFolder(f, f.source, true); onDismiss() }, size = 42.dp, iconSize = 22.dp)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("All folders", { selected = null }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Play", { vm.playFolder(sel, sel.source, false); onDismiss() }, icon = Icons.Rounded.PlayArrow)
                    SecondaryButton("Shuffle", { vm.playFolder(sel, sel.source, true); onDismiss() }, icon = Icons.Rounded.Shuffle)
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(folderTracks.size, key = { folderTracks[it].id }) { i ->
                        val t = folderTracks[i]
                        TrackRow(t, i + 1, current = false) {
                            vm.playTrack(t, QueueScope.Folder(sel.folderPath, t.source, sel.name)); onDismiss()
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ full track list

@UnstableApi
@Composable
fun TracksDialog(vm: MainViewModel, currentId: String?, onDismiss: () -> Unit) {
    val c = Nocturne.colors
    val query by vm.trackQuery.collectAsState()
    val count by vm.trackCount.collectAsState()
    val items = vm.tracksPaged.collectAsLazyPagingItems()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.trackQuery.value = "" } }

    FullDialog(
        onDismiss, "Library", "${"%,d".format(count)} indexed tracks", Icons.Rounded.LibraryMusic,
        headerActions = {
            // Icon only on a narrow (portrait) screen, or it squeezes the title away.
            if (isNarrowWindow()) {
                IconTile(Icons.Rounded.Shuffle, "Randomize", { vm.randomizeAll(); onDismiss() }, size = 44.dp, selected = true)
            } else {
                PrimaryButton("Randomize", { vm.randomizeAll(); onDismiss() }, icon = Icons.Rounded.Shuffle)
            }
            Spacer(Modifier.width(8.dp))
        },
        maxWidth = 800.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { vm.trackQuery.value = it }, "Search tracks", Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.Search, null, tint = c.textMuted)
            }
            if (items.itemCount == 0) EmptyHint(if (count == 0) "No tracks yet. Add music in Settings > Library." else "No tracks match your search.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(items.itemCount, key = items.itemKey { it.id }) { i ->
                    val t = items[i]
                    if (t != null) {
                        TrackRow(t, i + 1, current = t.id == currentId) { vm.playTrack(t, QueueScope.All); onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, number: Int, current: Boolean, onClick: () -> Unit) {
    val c = Nocturne.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .background(if (current) c.accentContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
            if (current) Icon(Icons.Rounded.PlayArrow, "Now playing", tint = c.accent, modifier = Modifier.size(22.dp))
            else Text("$number", color = c.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = if (current) c.onAccentContainer else c.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.artist} • ${t.album}", color = c.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(formatTime(t.durationMs), color = c.textMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Nocturne.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
