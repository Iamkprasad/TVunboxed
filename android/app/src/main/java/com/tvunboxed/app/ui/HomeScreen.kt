package com.tvunboxed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvunboxed.app.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel, onOpenRemote: () -> Unit, onOpenAir: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var vol by remember { mutableStateOf(55f) }
    var tipVisible by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionCard("TV stage") {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .padding(4.dp)
            ) {
                Text(
                    "${state.device?.name ?: "No TV"} · ${if (state.connected) "online" else "offline"}",
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenRemote, Modifier.weight(1f)) { Text("Remote") }
                Button(onClick = onOpenAir, Modifier.weight(1f)) { Text("Air mouse") }
            }
        }

        SectionCard("Now playing") {
            Text(state.mediaStatus.ifBlank { "Nothing playing" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.media("play") }) { Text("Play") }
                Button(onClick = { vm.media("pause") }) { Text("Pause") }
                Button(onClick = { vm.media("stop") }) { Text("Stop") }
            }
            Slider(value = vol, onValueChange = { vol = it }, valueRange = 0f..100f)
            Button(onClick = { vm.volume(vol.toInt()) }) { Text("Set volume ${vol.toInt()}") }
        }

        SectionCard("Quick actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.source(0) }, Modifier.weight(1f)) { Text("Input") }
                Button(onClick = { vm.openLink("https://example.com") }, Modifier.weight(1f)) { Text("Browser") }
                Button(onClick = { vm.refreshCapabilities() }, Modifier.weight(1f)) { Text("Identify") }
            }
        }

        SectionCard("TV health") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Link", fontWeight = FontWeight.Bold)
                    Text(if (state.connected) "online" else "offline")
                }
                Column(Modifier.weight(1f)) {
                    Text("Model", fontWeight = FontWeight.Bold)
                    Text(state.device?.model ?: "—")
                }
                Column(Modifier.weight(1f)) {
                    Text("Framebuffer", fontWeight = FontWeight.Bold)
                    Text(state.framebuffer?.let { "${it.first}×${it.second}" } ?: "—")
                }
            }
        }

        if (tipVisible) {
            SectionCard("Tip") {
                Text("Brightness and equaliser aren't available on this TV. Volume, mute and media keys live in the Remote.")
                Button(onClick = { tipVisible = false }) { Text("Dismiss") }
            }
        }
    }
}