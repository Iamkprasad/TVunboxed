package com.tvunboxed.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvunboxed.app.R
import com.tvunboxed.app.core.Capability
import com.tvunboxed.app.core.CapabilitySet
import com.tvunboxed.app.core.CapabilityStatus

// ---------- Fonts ----------
val TvSans = FontFamily.SansSerif   // Trebuchet MS fallback
val TvSerif = FontFamily.Serif      // Georgia fallback
val TvMono = FontFamily.Monospace   // Courier New fallback

// ---------- Design tokens (use TvTokens from Theme.kt) ----------
// Hard shadow colours handled per theme via LocalTvColors; keep simple: use TvTokens.

@Composable
fun TvIcon(name: String, size: Dp = 20.dp, tint: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val res = when (name) {
        "home" -> R.drawable.ic_home
        "folder" -> R.drawable.ic_folder
        "remote" -> R.drawable.ic_remote
        "tv" -> R.drawable.ic_tv
        "tools" -> R.drawable.ic_tools
        "power" -> R.drawable.ic_power
        "input" -> R.drawable.ic_input
        "mute" -> R.drawable.ic_mute
        "vol" -> R.drawable.ic_vol
        "mic" -> R.drawable.ic_mic
        "back" -> R.drawable.ic_back
        "menu" -> R.drawable.ic_menu
        "up" -> R.drawable.ic_up
        "down" -> R.drawable.ic_down
        "left" -> R.drawable.ic_left
        "right" -> R.drawable.ic_right
        "plus" -> R.drawable.ic_plus
        "minus" -> R.drawable.ic_minus
        "rew" -> R.drawable.ic_rew
        "ff" -> R.drawable.ic_ff
        "play" -> R.drawable.ic_play
        "pause" -> R.drawable.ic_pause
        "stop" -> R.drawable.ic_stop
        "star" -> R.drawable.ic_star
        "more" -> R.drawable.ic_more
        "gear" -> R.drawable.ic_gear
        "close" -> R.drawable.ic_close
        "check" -> R.drawable.ic_check
        "mouse" -> R.drawable.ic_mouse
        "kb" -> R.drawable.ic_kb
        "upload" -> R.drawable.ic_upload
        "refresh" -> R.drawable.ic_refresh
        "search" -> R.drawable.ic_search
        "video" -> R.drawable.ic_video
        "image" -> R.drawable.ic_image
        "audio" -> R.drawable.ic_audio
        "doc" -> R.drawable.ic_doc
        "live" -> R.drawable.ic_live
        "wifi" -> R.drawable.ic_wifi
        "battery" -> R.drawable.ic_battery
        "cpu" -> R.drawable.ic_cpu
        "globe" -> R.drawable.ic_globe
        "mirror" -> R.drawable.ic_mirror
        "info" -> R.drawable.ic_info
        "trash" -> R.drawable.ic_trash
        "warn" -> R.drawable.ic_warn
        "target" -> R.drawable.ic_target
        "expand" -> R.drawable.ic_expand
        "shrink" -> R.drawable.ic_shrink
        "shift" -> R.drawable.ic_shift
        "bksp" -> R.drawable.ic_bksp
        "enter" -> R.drawable.ic_enter
        "clip" -> R.drawable.ic_clip
        "sun" -> R.drawable.ic_sun
        "moon" -> R.drawable.ic_moon
        else -> R.drawable.ic_info
    }
    Icon(painter = painterResource(res), contentDescription = name, modifier = modifier.size(size), tint = tint)
}

// ---------- Card ----------
@Composable
fun TvCard(title: String? = null, hint: String? = null, onHint: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shadow = Color(0x73000000)
    val cardBg = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val line = androidx.compose.material3.MaterialTheme.colorScheme.outline
    Box(modifier) {
        Box(Modifier.matchParentSize().offset(3.dp, 3.dp).background(shadow))
        Column(Modifier.background(cardBg).border(1.dp, line).padding(14.dp)) {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontFamily = TvSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (hint != null) {
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.clickable { onHint?.invoke() }, verticalAlignment = Alignment.CenterVertically) {
                            Text(hint, fontFamily = TvMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFC5E17A))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

// ---------- Button ----------
enum class TvButtonVariant { Default, Blue, Green, Pink, Amber, Danger }
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TvButtonVariant = TvButtonVariant.Default,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg) = when (variant) {
        TvButtonVariant.Default -> Color(0xFF1F2238) to Color(0xFFEDEFFC)
        TvButtonVariant.Blue -> Color(0xFFADDBFF) to Color(0xFF003454)
        TvButtonVariant.Green -> Color(0xFFC5E17A) to Color(0xFF3F4E00)
        TvButtonVariant.Pink -> Color(0xFFFFBFD9) to Color(0xFF82004F)
        TvButtonVariant.Amber -> Color(0xFFD5CDFF) to Color(0xFF2A1159)
        TvButtonVariant.Danger -> Color(0xFFFFC3C3) to Color(0xFF880023)
    }
    val shadow = Color(0x66000000)
    val border = Color(0xFF636889)
    Box(modifier.height(46.dp)) {
        if (!pressed) Box(Modifier.matchParentSize().offset(2.dp, 2.dp).background(shadow))
        Box(
            Modifier
                .matchParentSize()
                .offset(if (pressed) 2.dp else 0.dp, if (pressed) 2.dp else 0.dp)
                .background(if (enabled) bg else bg.copy(alpha = 0.45f))
                .border(2.dp, border)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                content()
            }
        }
    }
}

@Composable
fun TvIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shadow = Color(0x66000000)
    val border = Color(0xFF636889)
    Box(modifier.size(42.dp)) {
        if (!pressed) Box(Modifier.matchParentSize().offset(2.dp, 2.dp).background(shadow))
        Box(
            Modifier.matchParentSize()
                .offset(if (pressed) 2.dp else 0.dp, if (pressed) 2.dp else 0.dp)
                .background(Color(0xFF1F2238))
                .border(2.dp, border)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

@Composable
fun TvChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shadow = Color(0x66000000)
    val border = Color(0xFF636889)
    Box(modifier.height(38.dp)) {
        if (active) Box(Modifier.matchParentSize().offset(2.dp, 2.dp).background(shadow))
        Box(
            Modifier.matchParentSize()
                .background(if (active) Color(0xFFD5CDFF) else Color(0xFF1F2238))
                .border(2.dp, border)
                .clickable { onClick() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (active) Color(0xFF2A1159) else Color(0xFFA6ACD6))
        }
    }
}

@Composable
fun TvSegmented(options: List<String>, selected: String, onSelect: (String) -> Unit, danger: Boolean = false, modifier: Modifier = Modifier) {
    val border = Color(0xFF636889)
    Row(modifier.border(2.dp, border).background(Color(0xFF0B0C16)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { opt ->
            val isSel = opt == selected
            val selBg = if (danger && isSel) Color(0xFFFFC3C3) else Color(0xFFD5CDFF)
            val selFg = if (danger && isSel) Color(0xFF880023) else Color(0xFF2A1159)
            Box(Modifier.weight(1f).height(40.dp)
                .background(if (isSel) selBg else Color.Transparent)
                .clickable { onSelect(opt) },
                contentAlignment = Alignment.Center) {
                Text(opt, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSel) selFg else Color(0xFFA6ACD6))
            }
        }
    }
}

@Composable
fun TvTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val border = Color(0xFF636889)
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(label, color = Color(0xFF8E94BD)) },
        singleLine = true,
        modifier = modifier.height(46.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF0B0C16),
            unfocusedContainerColor = Color(0xFF0B0C16),
            focusedIndicatorColor = Color(0xFFADDBFF),
            unfocusedIndicatorColor = border,
            cursorColor = Color(0xFFC5E17A)
        )
    )
}

@Composable
fun TvSwitchRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val border = Color(0xFF636889)
    Row(modifier.fillMaxWidth().height(46.dp).background(Color(0xFF1F2238)).border(2.dp, border).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp, 24.dp).background(if (on) Color(0xFFC5E17A) else Color(0xFF0B0C16)).border(2.dp, border), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.offset(x = if (on) 20.dp else 2.dp).size(16.dp).background(if (on) Color(0xFF3F4E00) else Color(0xFFA6ACD6)))
        }
    }
}

@Composable
fun StatusBanner2(status: String, busy: Boolean) {
    Text(if (busy) "Working… $status" else status, fontFamily = TvSans, fontSize = 12.sp, color = Color(0xFF8FD3C7), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
fun EmptyState2(icon: String, title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TvIcon(icon, size = 34.dp, tint = Color(0xFFA6ACD6))
        Text(title, fontFamily = TvSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFEDEFFC))
        Text(hint, fontFamily = TvSans, fontSize = 13.sp, color = Color(0xFFA6ACD6))
    }
}

// ---- Compatibility aliases for existing screens ----
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) = TvCard(title = title, modifier = modifier, content = content)

@Composable
fun StatusBanner(status: String, busy: Boolean) = StatusBanner2(status, busy)

@Composable
fun CapabilityChips(capabilities: CapabilitySet) = CapabilityChips2(capabilities)

@Composable
fun EmptyState(message: String, hint: String? = null) = EmptyState2("search", message, hint ?: "")

@Composable
fun CapabilityChips2(capabilities: CapabilitySet) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Capability.VENDOR_CONTROL, Capability.WEBDAV, Capability.MEDIA_CONTROL).forEach { c ->
            val st = capabilities.status(c)
            Box(Modifier.background(Color(0xFF1F2238)).border(1.dp, Color(0xFF636889)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("${c.name}: ${st.name}", fontFamily = TvMono, fontSize = 11.sp, color = if (st == CapabilityStatus.SUPPORTED) Color(0xFFC5E17A) else Color(0xFFA6ACD6))
            }
        }
    }
}