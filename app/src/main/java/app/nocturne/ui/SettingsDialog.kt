@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nocturne.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.nocturne.MainViewModel
import app.nocturne.data.AbsConfig
import app.nocturne.data.AppSettings
import app.nocturne.data.GainSource
import app.nocturne.data.SmbConfig
import app.nocturne.library.FolderListing
import app.nocturne.data.SmbSource
import app.nocturne.data.Source
import app.nocturne.library.TestResult
import app.nocturne.launcher.InstalledApps
import app.nocturne.ui.theme.Nocturne
import app.nocturne.ui.theme.NocturneColors
import app.nocturne.ui.theme.NocturnePalettes
import app.nocturne.ui.theme.ThemeId
import kotlinx.coroutines.launch

enum class SettingsTab(val label: String, val icon: ImageVector) {
    THEME("Theme", Icons.Rounded.Palette),
    LAUNCHER("Launcher", Icons.Rounded.Home),
    LIBRARY("Library", Icons.Rounded.Storage),
    AUDIO("Audio", Icons.Rounded.GraphicEq),
    AUDIOBOOKS("Audiobooks", Icons.Rounded.MenuBook),
}

@UnstableApi
@Composable
fun SettingsDialog(
    vm: MainViewModel,
    settings: AppSettings,
    initialTab: SettingsTab,
    hasAudioPermission: Boolean,
    homeSignal: Int,
    onRequestPermissions: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenApps: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Nocturne.colors
    var tab by remember { mutableStateOf(initialTab) }
    // The folder picker is drawn over the whole settings dialog (it cannot live inside the scrolling tab body).
    var picker by remember { mutableStateOf<FolderPickRequest?>(null) }
    FullDialog(onDismiss, "Settings", "Nocturne bedside player", Icons.Rounded.Settings, maxWidth = 860.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val body: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (tab) {
                        SettingsTab.THEME -> ThemeTab(vm, settings)
                        SettingsTab.LAUNCHER -> LauncherTab(vm, settings, homeSignal, onOpenApps)
                        SettingsTab.LIBRARY -> LibraryTab(vm, settings, hasAudioPermission, onRequestPermissions, onPickFolder) { picker = it }
                        SettingsTab.AUDIO -> AudioTab(vm, settings)
                        SettingsTab.AUDIOBOOKS -> AudiobooksTab(vm, settings)
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(190.dp).fillMaxHeight().background(c.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingsTab.entries.forEach { TabButton(it, it == tab, Modifier.fillMaxWidth()) { tab = it } }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                    Box(Modifier.weight(1f)) { body() }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(c.background).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingsTab.entries.forEach { TabButton(it, it == tab, Modifier.weight(1f), compact = true) { tab = it } }
                    }
                    Box(Modifier.width(1.dp).fillMaxWidth().height(1.dp).background(c.border))
                    Box(Modifier.weight(1f)) { body() }
                }
            }
        }
    }
    picker?.let { req ->
        FolderPickerDialog(vm, req, onDismiss = { picker = null }, onPick = { req.onPick(it); picker = null })
    }
}

@Composable
private fun TabButton(tab: SettingsTab, selected: Boolean, modifier: Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val c = Nocturne.colors
    Row(
        modifier.height(48.dp).clip(ControlShape)
            .background(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = if (compact) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(tab.icon, tab.label, tint = if (selected) c.onAccent else c.textSecondary, modifier = Modifier.size(22.dp))
        if (!compact) {
            Spacer(Modifier.width(10.dp))
            Text(tab.label, color = if (selected) c.onAccent else c.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ theme

@UnstableApi
@Composable
private fun ThemeTab(vm: MainViewModel, settings: AppSettings) {
    val c = Nocturne.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Bedside theme")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeId.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { id -> ThemeCard(id, id == settings.theme, Modifier.weight(1f).fillMaxHeight()) { vm.setTheme(id) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Text(
            "The whole app recolours: backgrounds, panels, text, buttons, sliders, the clock and the dim screen.",
            color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** A miniature of the real player drawn with that theme's own palette, so the choice is visible before tapping. */
@Composable
private fun ThemeCard(id: ThemeId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p: NocturneColors = NocturnePalettes.of(id)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(p.background)
            .border(if (selected) 3.dp else 1.dp, if (selected) p.accent else p.border, shape)
            .clickable(onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // mini header
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(5.dp)).background(p.surface).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 22.dp, height = 7.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 26.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(p.textPrimary))
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Brush.radialGradient(listOf(p.coverStart, p.coverEnd))), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).background(p.accent, CircleShape))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(p.textPrimary))
                Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.textSecondary))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.border)) {
                    Box(Modifier.fillMaxWidth(0.4f).height(4.dp).background(p.accent))
                }
            }
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(p.accent), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).background(p.onAccent, CircleShape))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(id.label, color = p.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(id.description, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(Modifier.size(26.dp).background(p.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Selected", tint = p.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ launcher

private fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}

@UnstableApi
@Composable
private fun LauncherTab(vm: MainViewModel, settings: AppSettings, homeSignal: Int, onOpenApps: () -> Unit) {
    val context = LocalContext.current
    var recheck by remember { mutableStateOf(0) }
    val isHome = remember(homeSignal, recheck) { isDefaultHome(context) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Home screen")
        StatusLine(
            isHome,
            if (isHome) "Nocturne is your home app. The Home button brings you back here."
            else "Nocturne is not your home app yet. Choose it under 'Set as default launcher'.",
        )
        ButtonFlow {
            PrimaryButton("Set as default launcher", {
                InstalledApps.openDefaultLauncherSettings(context); recheck++
            }, flowWeight(1.8f), icon = Icons.Rounded.Home)
            SecondaryButton("Refresh", { recheck++ }, flowWeight(), icon = Icons.Rounded.Refresh)
        }
        ButtonFlow {
            SecondaryButton("Open other apps", onOpenApps, flowWeight(), icon = Icons.Rounded.Apps)
            SecondaryButton("Android settings", { InstalledApps.openSystemSettings(context) }, flowWeight(), icon = Icons.Rounded.Settings)
        }
        SectionLabel("Display")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto-dim", color = Nocturne.colors.textPrimary, style = MaterialTheme.typography.titleSmall)
            Text(
                "Shows the dim clock screen by itself after this long without a touch. Tap the screen to wake it.",
                color = Nocturne.colors.textSecondary, style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((secs, label) in listOf(0 to "Never", 30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min")) {
                    val on = settings.autoDimSeconds == secs
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(ControlShape)
                            .background(if (on) Nocturne.colors.accentContainer else Nocturne.colors.surfaceHigh)
                            .border(1.dp, if (on) Nocturne.colors.accent.copy(alpha = 0.5f) else Nocturne.colors.border, ControlShape)
                            .clickable { vm.setAutoDimSeconds(secs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (on) Nocturne.colors.accent else Nocturne.colors.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        ToggleRow("Keep awake", "Screen stays on while Nocturne is open", settings.keepAwake) { vm.setKeepAwake(it) }
        ToggleRow("Fullscreen", "Hide the status and navigation bars (swipe from the edge to show them)", settings.fullscreen) { vm.setFullscreen(it) }
        Text(
            "To switch back to another launcher later, use 'Set as default launcher' above or Android settings > Apps > Default apps > Home app.",
            color = Nocturne.colors.textMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ------------------------------------------------------------------ library (samba + device)

private const val ADD_SHARE = "+add"

@UnstableApi
@Composable
private fun LibraryTab(
    vm: MainViewModel,
    settings: AppSettings,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onPickFolder: () -> Unit,
    onBrowse: (FolderPickRequest) -> Unit,
) {
    val c = Nocturne.colors
    val scope = rememberCoroutineScope()
    val lib by vm.libraryStatus.collectAsState()
    val count by vm.trackCount.collectAsState()
    val counts by vm.sourceCounts.collectAsState()
    val sources = settings.smbSources
    var selectedId by remember { mutableStateOf(Source.SAMBA) }
    // While adding a share, [draftId] is its new id and the form starts from the main share's server/user/domain.
    var draftId by remember { mutableStateOf<String?>(null) }
    val isNew = draftId != null
    val base: SmbSource = remember(draftId, selectedId, sources) {
        draftId?.let { id ->
            SmbSource(id, "", settings.smb.copy(subfolder = "", autoRescanMinutes = 30), includeInMain = false)
        } ?: sources.firstOrNull { it.id == selectedId } ?: sources.first()
    }
    val saved = base.config

    var name by remember(base.id) { mutableStateOf(base.name.takeIf { !isNew } ?: "") }
    var merge by remember(base.id, base.includeInMain) { mutableStateOf(base.includeInMain) }
    var host by remember(base.id, saved) { mutableStateOf(saved.host) }
    var share by remember(base.id, saved) { mutableStateOf(saved.share) }
    var sub by remember(base.id, saved) { mutableStateOf(saved.subfolder) }
    var user by remember(base.id, saved) { mutableStateOf(saved.username) }
    var domain by remember(base.id, saved) { mutableStateOf(saved.domain) }
    var rescan by remember(base.id, saved) { mutableStateOf(saved.autoRescanMinutes.toString()) }
    var password by remember(base.id) { mutableStateOf("") }
    var passwordEdited by remember(base.id) { mutableStateOf(false) }
    var test by remember(base.id) { mutableStateOf<TestResult?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmRemove by remember(base.id) { mutableStateOf(false) }

    // Once a newly added share shows up in the settings, switch from the draft form to the saved share.
    androidx.compose.runtime.LaunchedEffect(sources, draftId) {
        draftId?.let { id -> if (sources.any { it.id == id }) { selectedId = id; draftId = null } }
    }

    fun cfg() = SmbConfig(host, share, sub, user, domain, rescan.toIntOrNull() ?: 30)
    fun pw() = if (passwordEdited) password else null
    fun displayName() = name.trim().ifEmpty { sub.trim('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { share.trim() }.ifEmpty { "Share" } }
    fun current() = SmbSource(base.id, if (base.isMain) AppSettings.MAIN_NAME else displayName(), cfg(), merge || base.isMain, base.lastScan)

    BoxWithConstraints {
    val wide = maxWidth >= 560.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Samba / network shares")
        Text(
            "Added shares are kept out of Randomize and the default Library view unless 'Include in main library' is on.",
            color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
        )
        ChipRow(
            sources.map { it.id to it.name } + (ADD_SHARE to "+ Add share"),
            draftId?.let { ADD_SHARE } ?: base.id,
        ) { id ->
            if (id == ADD_SHARE) draftId = Source.newShareId() else { draftId = null; selectedId = id }
        }
        if (!base.isMain) {
            NocturneTextField(name, { name = it }, "Name", Modifier.fillMaxWidth(), "Review, Comedy ...")
            ToggleRow(
                "Include in main library", "On: its tracks join Randomize and the Library list. Off: only playable on their own.", merge,
            ) { merge = it; if (!isNew) vm.updateShareFlags(current()) }
        }
        val hostField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(host, { host = it }, "Server (IP or name[:port])", m, "nas.local or nas.local:445", monospace = true) }
        val shareField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(share, { share = it }, "Share name", m, "music", monospace = true) }
        val subField: @Composable (Modifier) -> Unit = { m ->
            Row(m, verticalAlignment = Alignment.CenterVertically) {
                NocturneTextField(sub, { sub = it }, "Subfolder (optional)", Modifier.weight(1f), "Bedside/Albums", monospace = true)
                Spacer(Modifier.width(8.dp))
                IconTile(
                    Icons.Rounded.FolderOpen, "Browse folders",
                    { onBrowse(FolderPickRequest(base.id, cfg(), pw(), isNew, sub) { sub = it }) },
                    size = 56.dp,
                )
            }
        }
        val userField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(user, { user = it }, "Username (empty = guest)", m) }
        val passField: @Composable (Modifier) -> Unit = { m ->
            NocturneTextField(
                password, { password = it; passwordEdited = true }, "Password", m,
                placeholder = when {
                    passwordEdited -> ""
                    vm.hasSmbPassword(base.id) -> "saved"
                    isNew && vm.reusesMainPassword(cfg()) -> "same as main share"
                    else -> ""
                },
                password = true,
            )
        }
        val domainField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(domain, { domain = it }, "Workgroup / domain", m) }
        val rescanField: @Composable (Modifier) -> Unit = { m ->
            NocturneTextField(
                rescan, { rescan = it.filter(Char::isDigit).take(4) }, "Auto-rescan (min, 0 = off)", m,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { hostField(Modifier.weight(1.3f)); shareField(Modifier.weight(1f)); subField(Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { userField(Modifier.weight(1f)); passField(Modifier.weight(1f)); domainField(Modifier.weight(1f)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { hostField(Modifier.weight(1f)); shareField(Modifier.weight(1f)) }
            subField(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { userField(Modifier.weight(1f)); passField(Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { domainField(Modifier.weight(1f)); rescanField(Modifier.weight(1f)) }
        }
        ButtonFlow {
            if (wide) rescanField(flowWeight())
            SecondaryButton(if (testing) "Testing..." else "Test connection", {
                scope.launch { testing = true; test = vm.testSmb(base.id, cfg(), pw(), isNew); testing = false }
            }, flowWeight(), icon = Icons.Rounded.Wifi, enabled = !testing && host.isNotBlank() && share.isNotBlank())
            PrimaryButton(if (lib.scanning) "Scanning..." else "Save & scan share", {
                vm.saveSmbAndScan(current(), pw(), isNew); passwordEdited = false
            }, flowWeight(), icon = Icons.Rounded.Dns, enabled = !lib.scanning && host.isNotBlank() && share.isNotBlank())
            if (!base.isMain && !isNew) {
                SecondaryButton(if (confirmRemove) "Tap again to remove" else "Remove share", {
                    if (confirmRemove) { vm.removeShare(base.id); selectedId = Source.SAMBA } else confirmRemove = true
                }, flowWeight(), icon = Icons.Rounded.Close)
            }
        }
        test?.let { r ->
            StatusLine(r.success, r.message + if (r.sample.isNotEmpty()) "\n" + r.sample.joinToString(", ") else "")
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        SectionLabel("Music on this device")
        ToggleRow("Include device music", "Everything Android has indexed (Music, Downloads, SD card)", settings.mediaStoreEnabled) { vm.setMediaStoreEnabled(it) }
        ToggleRow("Include in main library", "On: device music joins Randomize and the Library list.", settings.deviceInMain) { vm.setDeviceInMain(it) }
        if (!hasAudioPermission) {
            StatusLine(false, "Nocturne needs permission to read audio files on this device.")
            PrimaryButton("Grant music access", onRequestPermissions, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check)
        }
        Text("Extra folders (SD card or any directory):", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
        settings.localTreeUris.forEach { uri ->
            Row(
                Modifier.fillMaxWidth().clip(ControlShape).background(c.surfaceHigh).border(1.dp, c.border, ControlShape).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = c.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(Uri.decode(uri).substringAfterLast("tree/"), color = c.textPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconTile(Icons.Rounded.Close, "Remove folder", { vm.removeTree(uri) }, size = 44.dp, container = c.surfaceHigh)
            }
        }
        ButtonFlow {
            SecondaryButton("Add folder", onPickFolder, flowWeight(), icon = Icons.Rounded.Add)
            SecondaryButton("Rescan device", { vm.scanLocal() }, flowWeight(), icon = Icons.Rounded.Refresh, enabled = !lib.scanning)
        }
        val perSource = (settings.smbSources.map { it.id } + Source.LOCAL).filter { (counts[it] ?: 0) > 0 }
        StatusLine(
            null,
            "${"%,d".format(count)} tracks in the main library" +
                (if (settings.smbExtra.isNotEmpty() || !settings.deviceInMain) perSource.joinToString("") { "\n${settings.sourceName(it)}: ${"%,d".format(counts[it])}" } else "") +
                (lib.message?.let { "\n$it" } ?: ""),
        )
    }
    }
}

// ------------------------------------------------------------------ audio (loudness levelling)

@UnstableApi
@Composable
private fun AudioTab(vm: MainViewModel, settings: AppSettings) {
    val c = Nocturne.colors
    val counts by vm.gainCounts.collectAsState()
    fun n(source: Int) = counts[source] ?: 0
    val total = counts.values.sum()
    val known = n(GainSource.TAG) + n(GainSource.MEASURED)
    val pending = n(GainSource.UNKNOWN) + n(GainSource.UNTAGGED)
    val toMeasure = n(GainSource.UNTAGGED)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Loudness levelling")
        ToggleRow(
            "Level track loudness", "Plays every track at the same loudness, from ReplayGain tags or measured values.",
            settings.levelTracks,
        ) { vm.setLevelTracks(it) }
        Row(
            Modifier.fillMaxWidth().clip(ControlShape).background(c.surfaceHigh).border(1.dp, c.border, ControlShape).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Level offset", color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(
                    "The volume slider sets the loudness after levelling. Raise the offset if levelled music is quieter than before.",
                    color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            SecondaryButton("-1", { vm.setLevelOffsetDb(settings.levelOffsetDb - 1f) }, Modifier.width(64.dp))
            Text(
                "%+.0f dB".format(settings.levelOffsetDb), color = c.accent, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            SecondaryButton("+1", { vm.setLevelOffsetDb(settings.levelOffsetDb + 1f) }, Modifier.width(64.dp))
        }
        var matchNote by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        SecondaryButton("Match typical loudness", {
            scope.launch {
                matchNote = if (vm.matchOffsetToLibrary()) "Offset set so a typical track keeps the loudness it had without levelling."
                else "Not enough tracks measured yet. Try again once the scan has made some progress."
            }
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.GraphicEq)
        matchNote?.let { Text(it, color = c.textSecondary, style = MaterialTheme.typography.bodySmall) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        SectionLabel("Loudness scan")
        ToggleRow(
            "Measure loudness in background",
            "Reads ReplayGain tags, and measures the middle of songs without them. One track at a time, only while nothing is loading.",
            settings.gainScan,
        ) { vm.setGainScan(it) }
        StatusLine(
            null,
            "Loudness known for ${"%,d".format(known)} of ${"%,d".format(total)} tracks" +
                "\nFrom tags: ${"%,d".format(n(GainSource.TAG))} \u2022 Measured: ${"%,d".format(n(GainSource.MEASURED))}" +
                " \u2022 Failed: ${"%,d".format(n(GainSource.FAILED))}" +
                if (pending > 0) "\n${"%,d".format(pending)} left" + (if (toMeasure > 0) ", ${"%,d".format(toMeasure)} without a tag to measure" else "") +
                    (if (settings.gainScan) " (scanning in the background)" else " (scan is off)") else "",
        )
        SecondaryButton("Re-measure all", { vm.remeasureAll() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
        Text(
            "Tracks with a ReplayGain tag use the tag. Tags added to a file later replace a measured value when the file is rescanned. " +
                "Tracks that are not known yet play at the library's median level until they are.",
            color = c.textMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ------------------------------------------------------------------ audiobookshelf

@UnstableApi
@Composable
private fun AudiobooksTab(vm: MainViewModel, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val saved = settings.abs
    var url by remember(saved) { mutableStateOf(saved.serverUrl) }
    var user by remember(saved) { mutableStateOf(saved.username) }
    var pkg by remember(saved) { mutableStateOf(saved.appPackage) }
    var password by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun pw() = if (edited) password else null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Audiobookshelf server")
        NocturneTextField(url, { url = it }, "Server address", Modifier.fillMaxWidth(), "http://abs.local:13378", monospace = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NocturneTextField(user, { user = it }, "Username", Modifier.weight(1f))
            NocturneTextField(
                password, { password = it; edited = true }, "Password", Modifier.weight(1f),
                placeholder = if (vm.hasAbsPassword() && !edited) "saved" else "", password = true,
            )
        }
        NocturneTextField(pkg, { pkg = it }, "Audiobookshelf app package", Modifier.fillMaxWidth(), monospace = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(if (busy) "Testing..." else "Test", {
                scope.launch { busy = true; result = vm.testAbs(url, user, pw()); busy = false }
            }, Modifier.weight(1f), enabled = !busy && url.isNotBlank())
            PrimaryButton("Save", {
                scope.launch { vm.saveAbs(AbsConfig(url, user, pkg), pw()); edited = false; result = true to "Saved." }
            }, Modifier.weight(1f))
        }
        result?.let { StatusLine(it.first, it.second) }
        Text(
            "Used to show your last played book on the Audiobookshelf screen. Playing happens in the Audiobookshelf app itself.",
            color = Nocturne.colors.textMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ------------------------------------------------------------------ folder picker

/** What the folder picker needs to browse one share; [onPick] receives the chosen share-relative path ("" = whole share). */
class FolderPickRequest(
    val sourceId: String,
    val cfg: SmbConfig,
    val password: String?,
    val isNew: Boolean,
    val startPath: String,
    val onPick: (String) -> Unit,
)

/** Browses the folders of a Samba share so the subfolder does not have to be typed. */
@UnstableApi
@Composable
private fun FolderPickerDialog(vm: MainViewModel, req: FolderPickRequest, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Nocturne.colors
    var path by remember { mutableStateOf(req.startPath.trim().trim('/', '\\').replace('\\', '/')) }
    var listing by remember { mutableStateOf<FolderListing?>(null) }

    androidx.compose.runtime.LaunchedEffect(path) {
        listing = null
        var r = vm.listSmbFolders(req.sourceId, req.cfg, req.password, req.isNew, path)
        // A typed path that does not exist (yet): fall back to the share root instead of showing only an error.
        if (r.error != null && path.isNotEmpty() && "not found" in r.error!!) { path = ""; return@LaunchedEffect }
        listing = r
    }

    FullDialog(
        onDismiss, "Choose folder", "\\\\${req.cfg.host.trim()}\\${req.cfg.share.trim('/', '\\')}", Icons.Rounded.FolderOpen,
        maxWidth = 640.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    Icons.Rounded.ArrowUpward, "Up one folder", { if (path.isNotEmpty()) path = path.substringBeforeLast('/', "") },
                    size = 44.dp, tint = if (path.isEmpty()) c.textMuted else null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "/" + path, color = c.textPrimary, style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val l = listing
                when {
                    l == null -> Text("Loading...", color = c.textMuted, modifier = Modifier.padding(20.dp))
                    l.error != null -> StatusLine(false, l.error, Modifier.padding(14.dp))
                    l.folders.isEmpty() -> Text("No sub-folders here.", color = c.textMuted, modifier = Modifier.padding(20.dp))
                    else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        items(l.folders.size, key = { l.folders[it] }) { i ->
                            val name = l.folders[i]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(ControlShape)
                                    .clickable { path = if (path.isEmpty()) name else "$path/$name" }.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.FolderOpen, null, tint = c.accent, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(name, color = c.textPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    if (path.isEmpty()) "Use whole share" else "Use this folder", { onPick(path) }, Modifier.weight(2f),
                    icon = Icons.Rounded.Check,
                )
            }
        }
    }
}
