package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.nocturne.ui.theme.Nocturne

/** True when the app hides the system bars; dialogs then hide them too. */
val LocalFullscreen = compositionLocalOf { false }

val CardShape = RoundedCornerShape(20.dp)
val ControlShape = RoundedCornerShape(14.dp)

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    color: Color = Nocturne.colors.surface,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, Nocturne.colors.border, shape),
        contentAlignment = contentAlignment,
    ) { content() }
}

/** Square-ish icon button used all over the transport and header. */
@Composable
fun IconTile(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    selected: Boolean = false,
    tint: Color? = null,
    container: Color? = null,
    selectedContainer: Color = Nocturne.colors.accentContainer,
    selectedTint: Color = Nocturne.colors.accent,
) {
    val c = Nocturne.colors
    val bg = if (selected) selectedContainer else container ?: c.surfaceHigh
    val fg = if (selected) selectedTint else tint ?: c.textSecondary
    Box(
        modifier
            .size(size)
            .clip(ControlShape)
            .background(bg)
            .border(1.dp, if (selected) selectedTint.copy(alpha = 0.5f) else c.border, ControlShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = fg, modifier = Modifier.size(iconSize)) }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = Nocturne.colors.accent,
    content: Color = Nocturne.colors.onAccent,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .height(48.dp)
            .clip(ControlShape)
            .background(container.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = Nocturne.colors.textPrimary,
) {
    val c = Nocturne.colors
    Row(
        modifier
            .height(48.dp)
            .clip(ControlShape)
            .background(c.surfaceHigh)
            .border(1.dp, c.border, ControlShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) tint else c.textMuted
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Nocturne.colors.textMuted,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}

@Composable
fun StatusLine(ok: Boolean?, text: String, modifier: Modifier = Modifier) {
    val c = Nocturne.colors
    val color = when (ok) { true -> c.positive; false -> c.danger; null -> c.textSecondary }
    Box(
        modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), ControlShape)
            .padding(12.dp),
    ) { Text(text, color = color, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val c = Nocturne.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(c.surfaceHigh)
            .border(1.dp, c.border, ControlShape)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.textSecondary,
                uncheckedTrackColor = c.surface,
                uncheckedBorderColor = c.border,
            ),
        )
    }
}

@Composable
fun NocturneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    monospace: Boolean = false,
) {
    val c = Nocturne.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = c.textMuted) }) else null,
        singleLine = true,
        shape = ControlShape,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.border,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
        ),
    )
}

@Composable
fun sliderColors() = Nocturne.colors.let {
    SliderDefaults.colors(
        thumbColor = it.accent,
        activeTrackColor = it.accent,
        inactiveTrackColor = it.border,
    )
}

/**
 * Slider that takes ~36 dp of height instead of Material's 48 dp touch target. The thumb may overhang the box slightly,
 * which is invisible; this is what lets the transport fit on a 360 dp tall landscape screen.
 */
@Composable
fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Box(modifier.height(36.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = value.coerceIn(0f, 1f), onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
            enabled = enabled, colors = sliderColors(), modifier = Modifier.wrapContentHeight(unbounded = true),
        )
    }
}

/**
 * Full-window dialog used for all modal screens. Sized for the phone in either orientation,
 * scrolls its own content, and keeps the system bars hidden when the app is in fullscreen mode.
 */
@Composable
fun FullDialog(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    maxWidth: Dp = 720.dp,
    content: @Composable () -> Unit,
) {
    val c = Nocturne.colors
    // Drawn inside the activity window, not as a Dialog window: a second window pulled the system bars back in on open/close.
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {},
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accentContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.textPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, color = c.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                headerActions()
                IconTile(Icons.Rounded.Close, "Close", onDismiss, size = 44.dp, container = c.surface)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(total / 60, total % 60)
}

