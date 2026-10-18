package app.nocturne.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.nocturne.launcher.AppEntry
import app.nocturne.launcher.InstalledApps
import app.nocturne.ui.theme.Nocturne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The part of a launcher that isn't a music player: reach any other installed app. */
@Composable
fun AppsDialog(onDismiss: () -> Unit) {
    val c = Nocturne.colors
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { InstalledApps.list(context) } }

    FullDialog(onDismiss, "All apps", "${apps.size} apps", Icons.Rounded.Apps, maxWidth = 800.dp) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                val icon = remember(app.component) { app.icon.toBitmap(128, 128).asImageBitmap() }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable { InstalledApps.launch(context, app); onDismiss() }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(icon, app.label, Modifier.size(52.dp))
                    Text(app.label, color = c.textPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
