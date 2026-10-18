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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
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
import app.nocturne.data.SmbConfig
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
    FullDialog(onDismiss, "Settings", "Nocturne bedside player", Icons.Rounded.Settings, maxWidth = 860.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val body: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (tab) {
                        SettingsTab.THEME -> ThemeTab(vm, settings)
                        SettingsTab.LAUNCHER -> LauncherTab(vm, settings, homeSignal, onOpenApps)
                        SettingsTab.LIBRARY -> LibraryTab(vm, settings, hasAudioPermission, onRequestPermissions, onPickFolder)
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Set as default launcher", {
                InstalledApps.openDefaultLauncherSettings(context); recheck++
            }, Modifier.weight(1f), icon = Icons.Rounded.Home)
            SecondaryButton("Refresh", { recheck++ }, icon = Icons.Rounded.Refresh)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Open other apps", onOpenApps, Modifier.weight(1f), icon = Icons.Rounded.Apps)
            SecondaryButton("Android settings", { InstalledApps.openSystemSettings(context) }, Modifier.weight(1f), icon = Icons.Rounded.Settings)
        }
        SectionLabel("Display")
        ToggleRow("Keep awake", "Screen stays on while Nocturne is open", settings.keepAwake) { vm.setKeepAwake(it) }
        ToggleRow("Fullscreen", "Hide the status and navigation bars (swipe from the edge to show them)", settings.fullscreen) { vm.setFullscreen(it) }
        Text(
            "To switch back to another launcher later, use 'Set as default launcher' above or Android settings > Apps > Default apps > Home app.",
            color = Nocturne.colors.textMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ------------------------------------------------------------------ library (samba + device)

@UnstableApi
@Composable
private fun LibraryTab(
    vm: MainViewModel,
    settings: AppSettings,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onPickFolder: () -> Unit,
) {
    val c = Nocturne.colors
    val scope = rememberCoroutineScope()
    val lib by vm.libraryStatus.collectAsState()
    val count by vm.trackCount.collectAsState()
    val saved = settings.smb

    var host by remember(saved) { mutableStateOf(saved.host) }
    var share by remember(saved) { mutableStateOf(saved.share) }
    var sub by remember(saved) { mutableStateOf(saved.subfolder) }
    var user by remember(saved) { mutableStateOf(saved.username) }
    var domain by remember(saved) { mutableStateOf(saved.domain) }
    var rescan by remember(saved) { mutableStateOf(saved.autoRescanMinutes.toString()) }
    var password by remember { mutableStateOf("") }
    var passwordEdited by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<TestResult?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun cfg() = SmbConfig(host, share, sub, user, domain, rescan.toIntOrNull() ?: 30)
    fun pw() = if (passwordEdited) password else null

    BoxWithConstraints {
    val wide = maxWidth >= 560.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Samba / network share")
        val hostField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(host, { host = it }, "Server (IP or name[:port])", m, "nas.local or nas.local:445", monospace = true) }
        val shareField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(share, { share = it }, "Share name", m, "music", monospace = true) }
        val subField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(sub, { sub = it }, "Subfolder (optional)", m, "Bedside/Albums", monospace = true) }
        val userField: @Composable (Modifier) -> Unit = { m -> NocturneTextField(user, { user = it }, "Username (empty = guest)", m) }
        val passField: @Composable (Modifier) -> Unit = { m ->
            NocturneTextField(
                password, { password = it; passwordEdited = true }, "Password", m,
                placeholder = if (vm.hasSmbPassword() && !passwordEdited) "saved" else "", password = true,
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide) rescanField(Modifier.weight(1f))
            SecondaryButton(if (testing) "Testing..." else "Test connection", {
                scope.launch { testing = true; test = vm.testSmb(cfg(), pw()); testing = false }
            }, Modifier.weight(1f), icon = Icons.Rounded.Wifi, enabled = !testing && host.isNotBlank() && share.isNotBlank())
            PrimaryButton(if (lib.scanning) "Scanning..." else "Save & scan share", {
                vm.saveSmbAndScan(cfg(), pw()); passwordEdited = false
            }, Modifier.weight(1f), icon = Icons.Rounded.Dns, enabled = !lib.scanning && host.isNotBlank() && share.isNotBlank())
        }
        test?.let { r ->
            StatusLine(r.success, r.message + if (r.sample.isNotEmpty()) "\n" + r.sample.joinToString(", ") else "")
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        SectionLabel("Music on this device")
        ToggleRow("Include device music", "Everything Android has indexed (Music, Downloads, SD card)", settings.mediaStoreEnabled) { vm.setMediaStoreEnabled(it) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Add folder", onPickFolder, Modifier.weight(1f), icon = Icons.Rounded.Add)
            SecondaryButton("Rescan device", { vm.scanLocal() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh, enabled = !lib.scanning)
        }
        StatusLine(null, "${"%,d".format(count)} tracks in the library" + (lib.message?.let { "\n$it" } ?: ""))
    }
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
