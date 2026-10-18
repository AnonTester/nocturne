package app.nocturne.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.nocturne.MainViewModel
import app.nocturne.abs.AbsBook
import app.nocturne.abs.AbsState
import app.nocturne.data.AbsConfig
import app.nocturne.launcher.AbsLauncher
import app.nocturne.ui.theme.Nocturne

/** Replica of the Audiobookshelf home-screen widget: last played book plus resume/open actions. */
@UnstableApi
@Composable
fun AudiobookshelfStage(vm: MainViewModel, cfg: AbsConfig, onSettings: () -> Unit) {
    val c = Nocturne.colors
    val context = LocalContext.current
    val state by vm.absState.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cfg.serverUrl, cfg.username) { vm.refreshAbs() }

    val book = (state as? AbsState.Ready)?.book
    val cover = (state as? AbsState.Ready)?.cover

    BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
        val landscape = maxWidth > maxHeight
        val bookPanel: @Composable (Modifier) -> Unit = { m ->
            Panel(m) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.aspectRatio(1f).fillMaxSize().clip(RoundedCornerShape(18.dp))
                                .background(c.secondary.copy(alpha = 0.14f)).border(1.dp, c.border, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cover != null) {
                                Image(cover.asImageBitmap(), book?.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), colorFilter = nightTint(c))
                            } else if (state is AbsState.Loading) {
                                CircularProgressIndicator(color = c.secondary)
                            } else {
                                Icon(Icons.Rounded.MenuBook, null, tint = c.secondary, modifier = Modifier.size(64.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    BookText(book, state)
                }
            }
        }
        val actions: @Composable (Modifier) -> Unit = { m ->
            Panel(m) {
                // fillMaxSize here let this panel take the whole portrait column and squeeze the book panel to nothing.
                Column((if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(16.dp), verticalArrangement = Arrangement.Center) {
                    SectionLabel("Audiobookshelf")
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        "Open Audiobookshelf", { message = AbsLauncher.open(context, cfg) },
                        Modifier.fillMaxWidth().height(60.dp),
                        icon = Icons.Rounded.OpenInNew, container = c.secondary, content = c.onSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Refresh", { vm.refreshAbs() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                        SecondaryButton("Settings", onSettings, Modifier.weight(1f), icon = Icons.Rounded.Settings)
                    }
                    (state as? AbsState.Unavailable)?.let {
                        Spacer(Modifier.height(10.dp)); StatusLine(false, it.reason)
                    }
                    message?.let { Spacer(Modifier.height(10.dp)); StatusLine(null, it) }
                    Spacer(Modifier.height(10.dp))
                    Text("Music is paused while Audiobookshelf is selected.", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bookPanel(Modifier.weight(0.4f).fillMaxHeight())
                actions(Modifier.weight(0.6f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bookPanel(Modifier.weight(1f).fillMaxWidth())
                actions(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BookText(book: AbsBook?, state: AbsState) {
    val c = Nocturne.colors
    val centered = Modifier.fillMaxWidth()
    when {
        book != null -> {
            Text(book.title, color = c.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = centered)
            if (book.author.isNotEmpty()) Text(book.author, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = centered)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.border)) {
                Box(Modifier.fillMaxWidth(book.progress.toFloat()).height(6.dp).background(c.secondary))
            }
            val left = book.remainingSec.toLong()
            Text(
                "${(book.progress * 100).toInt()}% • ${left / 3600}h ${(left % 3600) / 60}m left",
                color = c.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp),
            )
        }
        state is AbsState.Ready -> Text("No recently played audiobook", color = c.textSecondary, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        state is AbsState.NotConfigured -> Text("Add your server in Settings > Audiobooks to see your last played book here.", color = c.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        state is AbsState.Unavailable -> Text("Can't reach Audiobookshelf", color = c.textSecondary, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        else -> Text("Loading...", color = c.textSecondary, style = MaterialTheme.typography.titleSmall)
    }
}
