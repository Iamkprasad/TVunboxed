package com.tvunboxed.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvunboxed.app.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel, onOpenRemote: () -> Unit, onOpenAir: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var vol by remember { mutableStateOf(55f) }
    var tipVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // TV stage card
        TvCard {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(
                Brush.linearGradient(listOf(Color(0xFF2A1159), Color(0xFF0E0F1A), Color(0xFF1A3A44)))
            ).border(2.dp, Color(0xFF636889))) {
                // scanlines
                // wandering dot
                Box(Modifier.size(14.dp).background(Color(0xFFC5E17A)).align(Alignment.TopStart).padding(start = 12.dp, top = 20.dp))
                Text("▶ ${state.mediaStatus.ifBlank { "holiday.mp4" }}", Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0x8C000000)).padding(horizontal = 8.dp, vertical = 3.dp), fontFamily = TvMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFEDEFFC))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.device?.name ?: "Living Room", fontFamily = TvSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFEDEFFC))
                    Text("${state.device?.model ?: "E65"} · ${if (state.connected) "online" else "offline"} · 12 ms", fontFamily = TvMono, fontSize = 12.sp, color = Color(0xFFA6ACD6))
                }
                // signal bars
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    listOf(6, 11, 16, 22).forEach { h -> Box(Modifier.width(6.dp).height(h.dp).background(Color(0xFFC5E17A))) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(onClick = onOpenRemote, variant = TvButtonVariant.Amber, modifier = Modifier.weight(1f)) { TvIcon("remote", size = 20.dp, tint = Color(0xFF2A1159)); Text("Remote") }
                TvButton(onClick = onOpenAir, variant = TvButtonVariant.Blue, modifier = Modifier.weight(1f)) { TvIcon("mouse", size = 20.dp, tint = Color(0xFF003454)); Text("Air mouse") }
            }
        }
        // Now playing
        TvCard(title = "Now playing", hint = if (playing) "Playing" else "Paused") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Color(0xFFADDBFF)).border(2.dp, Color(0xFF636889)), contentAlignment = Alignment.Center) { TvIcon("video", size = 30.dp, tint = Color(0xFF1C1C1C)) }
                Column(Modifier.weight(1f)) {
                    Text(state.mediaStatus.ifBlank { "holiday.mp4" }, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFEDEFFC), maxLines = 1)
                    Text("From phone share", fontFamily = TvMono, fontSize = 12.sp, color = Color(0xFFA6ACD6))
                }
            }
            Spacer(Modifier.height(6.dp))
            // seek slider (use material slider for now)
            Slider(value = vol, onValueChange = { vol = it }, valueRange = 0f..100f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1:24", fontFamily = TvMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA6ACD6))
                Text("45:00", fontFamily = TvMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA6ACD6))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(onClick = { vm.media("rew") }, modifier = Modifier.weight(1f)) { TvIcon("rew", size = 20.dp, tint = Color(0xFFEDEFFC)) }
                TvButton(onClick = { playing = !playing; vm.media(if (playing) "play" else "pause") }, variant = TvButtonVariant.Green, modifier = Modifier.weight(1f)) { TvIcon(if (playing) "pause" else "play", size = 20.dp, tint = Color(0xFF3F4E00)) }
                TvButton(onClick = { vm.media("ff") }, modifier = Modifier.weight(1f)) { TvIcon("ff", size = 20.dp, tint = Color(0xFFEDEFFC)) }
                TvButton(onClick = { vm.media("stop") }, modifier = Modifier.weight(1f)) { TvIcon("stop", size = 20.dp, tint = Color(0xFFEDEFFC)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvIconButton(onClick = { vm.mute() }) { TvIcon("vol", size = 20.dp, tint = Color(0xFFEDEFFC)) }
                Slider(value = vol, onValueChange = { vol = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f))
                Text(vol.toInt().toString(), fontFamily = TvMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEDEFFC))
            }
        }
        // Quick actions
        TvCard(title = "Quick actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("input" to "Input", "globe" to "Browser", "mirror" to "Mirror", "cpu" to "Identify").forEach { (ic, lb) ->
                    TvButton(onClick = { }, modifier = Modifier.weight(1f).height(68.dp), variant = TvButtonVariant.Default) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { TvIcon(ic, size = 20.dp, tint = Color(0xFFEDEFFC)); Text(lb, fontSize = 12.sp) }
                    }
                }
            }
        }
        // TV health
        TvCard(title = "TV health", hint = "Refresh", onHint = { }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("wifi","Link","12 ms"), Triple("tv","Model","E65"), Triple("battery","Phone battery","93%"), Triple("globe","Network","4G")).forEach { (ic, t, v) ->
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { TvIcon(ic, size = 14.dp, tint = Color(0xFFA6ACD6)); Text(t, fontFamily = TvSans, fontSize = 12.sp, color = Color(0xFFA6ACD6)) }
                        Text(v, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (t == "Link" || t == "Phone battery") Color(0xFFC5E17A) else Color(0xFFADDBFF))
                        Text(if (t == "Link") "latency" else if (t == "Model") "from features" else if (t == "Phone battery") "charging" else "1.5 Mb/s", fontFamily = TvMono, fontSize = 11.sp, color = Color(0xFF8E94BD))
                    }
                }
            }
        }
        if (tipVisible) {
            TvCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvIcon("info", size = 20.dp, tint = Color(0xFFADDBFF))
                    Text("Brightness and equaliser aren't available on this TV. Volume, mute and media keys live in the Remote.", fontFamily = TvSans, fontSize = 13.sp, color = Color(0xFFA6ACD6), modifier = Modifier.weight(1f))
                    TvIconButton(onClick = { tipVisible = false }) { TvIcon("close", size = 14.dp, tint = Color(0xFFEDEFFC)) }
                }
            }
        }
    }
}