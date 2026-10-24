package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
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
import app.nocturne.data.AppSettings
import app.nocturne.data.FolderRow
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.playback.QueueScope
import app.nocturne.ui.theme.Nocturne

/**
 * Low search box (40 dp) with its own text state (a Flow-backed value drops IME input); the Search key closes the keyboard.
 * The list dialogs are short in landscape, so the usual 56 dp outlined field would eat a whole result row.
 */
@Composable
private fun SearchField(initial: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Nocturne.colors
    var text by remember { mutableStateOf(initial) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.foundation.text.BasicTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        modifier = modifier.height(40.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().clip(ControlShape).background(c.surfaceHigh).border(1.dp, c.border, ControlShape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(placeholder, color = c.textMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    inner()
                }
                if (text.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, "Clear search", tint = c.textMuted,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).clickable { text = ""; onChange("") }.padding(3.dp),
                    )
                }
            }
        },
    )
}

/** Source chips are only useful when something is kept out of the merged library, so a single-share setup shows none. */
private fun hasSourceChips(settings: AppSettings) = !(settings.smbExtra.isEmpty() && settings.deviceInMain)

/**
 * Chips to switch the list between the merged library and one source, as one compact scrolling line.
 * [selected] null = the merged library.
 */
@Composable
private fun SourceChips(settings: AppSettings, counts: Map<String, Int>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (!hasSourceChips(settings)) return
    val ids = settings.smbSources.map { it.id } + Source.LOCAL
    // Sources without tracks (e.g. device music with no permission) are not worth a chip, except the one that is selected.
    val shown = ids.filter { (counts[it] ?: 0) > 0 || it == selected }
    val options = listOf<Pair<String?, String>>(null to "Merged library") +
        shown.map { it to "${settings.sourceName(it)} (${"%,d".format(counts[it] ?: 0)})" }
    ChipRow(options, selected, modifier, scroll = true, onSelect = onSelect)
}

/** Search and source chips on one line (stacked on a narrow screen), the only chrome above the list. */
@Composable
private fun ListControls(
    query: String, onQuery: (String) -> Unit, placeholder: String,
    settings: AppSettings, counts: Map<String, Int>, source: String?, onSource: (String?) -> Unit,
) {
    val pad = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    if (!hasSourceChips(settings)) {
        SearchField(query, onQuery, placeholder, pad)
    } else if (isNarrowWindow()) {
        Column(pad, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(query, onQuery, placeholder, Modifier.fillMaxWidth())
            SourceChips(settings, counts, source, onSource)
        }
    } else {
        Row(pad, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, onQuery, placeholder, Modifier.weight(1f))
            SourceChips(settings, counts, source, onSource, Modifier.weight(1.3f))
        }
    }
}

/** A track opened from the merged list plays in the main library; opened under a source chip it plays inside that source. */
private fun queueFor(source: String?, track: Track, settings: AppSettings): QueueScope =
    source?.let { QueueScope.Source(it, settings.sourceName(it)) } ?: QueueScope.All

// ------------------------------------------------------------------ albums / folders

@UnstableApi
@Composable
fun AlbumsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = Nocturne.colors
    val query by vm.folderQuery.collectAsState()
    val folders by vm.folders.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<FolderRow?>(null) }
    val settings by vm.settings.collectAsState()
    val counts by vm.sourceCounts.collectAsState()
    val source by vm.folderSource.collectAsState()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.folderQuery.value = ""; vm.folderSource.value = null } }
    var folderTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // Kept here, outside the list/folder switch, so coming back from a folder lands where the list was left.
    val gridState = rememberLazyGridState()
    LaunchedEffect(query, source) { gridState.scrollToItem(0) }

    LaunchedEffect(selected) { folderTracks = selected?.let { vm.tracksIn(it.folderPath, it.source) } ?: emptyList() }

    val sel = selected
    val narrow = isNarrowWindow()
    FullDialog(
        onDismiss,
        title = sel?.displayName ?: "Folders & Albums",
        subtitle = sel?.let { "${folderTracks.size} tracks" } ?: "${folders.size} folders",
        icon = Icons.Rounded.FolderOpen,
        // Landscape: the actions live in the dialog header, so the list gets all the height. Portrait is narrow, so they go on a row under it.
        headerActions = {
            if (narrow) return@FullDialog
            if (sel != null) {
                IconTile(Icons.AutoMirrored.Rounded.ArrowBack, "All folders", { selected = null }, size = 44.dp)
                Spacer(Modifier.width(8.dp))
                IconTile(Icons.Rounded.PlayArrow, "Play folder", { vm.playFolder(sel, sel.source, false); onDismiss() }, size = 44.dp, selected = true)
                Spacer(Modifier.width(8.dp))
                IconTile(Icons.Rounded.Shuffle, "Shuffle folder", { vm.playFolder(sel, sel.source, true); onDismiss() }, size = 44.dp)
                Spacer(Modifier.width(8.dp))
            } else source?.let { id ->
                val name = settings.sourceName(id)
                IconTile(Icons.Rounded.PlayArrow, "Play $name in order", { vm.playSource(id, false); onDismiss() }, size = 44.dp)
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Shuffle $name", { vm.playSource(id, true); onDismiss() }, icon = Icons.Rounded.Shuffle)
                Spacer(Modifier.width(8.dp))
            }
        },
    ) {
        if (sel == null) {
            Column(Modifier.fillMaxSize()) {
                ListControls(query, { vm.folderQuery.value = it }, "Search folders or artists", settings, counts, source, { vm.folderSource.value = it })
                source?.takeIf { narrow }?.let { id ->
                    val name = settings.sourceName(id)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Shuffle $name", { vm.playSource(id, true); onDismiss() }, Modifier.weight(1f), icon = Icons.Rounded.Shuffle)
                        SecondaryButton("In order", { vm.playSource(id, false); onDismiss() }, icon = Icons.Rounded.PlayArrow)
                    }
                }
                if (folders.isEmpty()) EmptyHint("No folders yet. Add music in Settings > Library.")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(folders, key = { it.source + "|" + it.folderPath + "|" + it.name }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                                .clickable { selected = f }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accentContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Album, null, tint = c.accent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.displayName, color = c.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${f.trackCount} tracks • ${f.sampleArtist}", color = c.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconTile(Icons.Rounded.PlayArrow, "Play folder", { vm.playFolder(f, f.source, false); onDismiss() }, size = 40.dp, iconSize = 22.dp)
                            Spacer(Modifier.width(6.dp))
                            IconTile(Icons.Rounded.Shuffle, "Shuffle folder", { vm.playFolder(f, f.source, true); onDismiss() }, size = 40.dp, iconSize = 22.dp)
                        }
                    }
                }
            }
        } else {
            // Registered after the dialog's own handler, so Back steps out of the folder before it closes the dialog.
            androidx.activity.compose.BackHandler { selected = null }
            if (narrow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("All folders", { selected = null }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Play", { vm.playFolder(sel, sel.source, false); onDismiss() }, icon = Icons.Rounded.PlayArrow)
                    SecondaryButton("Shuffle", { vm.playFolder(sel, sel.source, true); onDismiss() }, icon = Icons.Rounded.Shuffle)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(folderTracks.size, key = { folderTracks[it].id }) { i ->
                    val t = folderTracks[i]
                    TrackRow(t, i + 1, current = false) {
                        vm.playTrack(t, QueueScope.Folder(sel.folderPath, t.source, sel.displayName)); onDismiss()
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
    val query by vm.trackQuery.collectAsState()
    val count by vm.trackCount.collectAsState()
    val items = vm.tracksPaged.collectAsLazyPagingItems()
    val settings by vm.settings.collectAsState()
    val counts by vm.sourceCounts.collectAsState()
    val source by vm.trackSource.collectAsState()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.trackQuery.value = ""; vm.trackSource.value = null } }

    FullDialog(
        onDismiss, "Library", source?.let { "${settings.sourceName(it)}: ${"%,d".format(items.itemCount)} tracks" } ?: "${"%,d".format(count)} indexed tracks", Icons.Rounded.LibraryMusic,
        headerActions = {
            // Randomize is the whole main library; with a source picked the same button shuffles just that source.
            val act = { source?.let { vm.playSource(it, true) } ?: vm.randomizeAll(); onDismiss() }
            // Icon only on a narrow (portrait) screen, or it squeezes the title away.
            if (isNarrowWindow()) {
                IconTile(Icons.Rounded.Shuffle, if (source == null) "Randomize" else "Shuffle ${settings.sourceName(source!!)}", act, size = 44.dp, selected = true)
            } else {
                PrimaryButton(if (source == null) "Randomize" else "Shuffle ${settings.sourceName(source!!)}", act, icon = Icons.Rounded.Shuffle)
            }
            Spacer(Modifier.width(8.dp))
        },
        maxWidth = 800.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            ListControls(query, { vm.trackQuery.value = it }, "Search tracks", settings, counts, source, { vm.trackSource.value = it })
            if (items.itemCount == 0) EmptyHint(if (count == 0) "No tracks yet. Add music in Settings > Library." else "No tracks match your search.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(items.itemCount, key = items.itemKey { it.id }) { i ->
                    val t = items[i]
                    if (t != null) {
                        TrackRow(t, i + 1, current = t.id == currentId) {
                            vm.playTrack(t, queueFor(source, t, settings)); onDismiss()
                        }
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
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
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
