package com.tvunboxed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvunboxed.app.MainViewModel

private const val KEY_POWER = 26
private const val KEY_HOME = 3
private const val KEY_BACK = 4
private const val KEY_MENU = 82
private const val KEY_UP = 19
private const val KEY_DOWN = 20
private const val KEY_LEFT = 21
private const val KEY_RIGHT = 22
private const val KEY_ENTER = 23
private const val KEY_VOL_UP = 24
private const val KEY_VOL_DOWN = 25
private const val KEY_MUTE = 78
private const val KEY_CHANNEL_UP = 166
private const val KEY_CHANNEL_DOWN = 167
private const val KEY_PLAY = 85
private const val KEY_PAUSE = 127
private const val KEY_NEXT = 87
private const val KEY_PREV = 88

@Composable
fun RemoteScreen(vm: MainViewModel) {
    var volume by remember { mutableFloatStateOf(20f) }
    var text by remember { mutableStateOf("") }
    var macroPackage by remember { mutableStateOf("") }
    var mouseOn by remember { mutableStateOf(false) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCard("Connection") {
            CapabilityChips(state.capabilities)
            Text("Device: ${state.device?.name ?: "not connected"}")
            Text("Framebuffer: ${state.framebuffer ?: "unknown"}")
            Button(onClick = { vm.refreshCapabilities() }) { Text("Re-detect capabilities") }
        }

        SectionCard("Keys") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { vm.key(KEY_POWER) }) { Text("POWER") }
                Button(onClick = { vm.key(KEY_HOME) }) { Text("HOME") }
                Button(onClick = { vm.key(KEY_BACK) }) { Text("BACK") }
                Button(onClick = { vm.key(KEY_MENU) }) { Text("MENU") }
            }
            Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { vm.key(KEY_UP) }) { Text("▲") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.key(KEY_LEFT) }) { Text("◀") }
                    Button(onClick = { vm.key(KEY_ENTER, longPress = true) }) { Text("OK") }
                    Button(onClick = { vm.key(KEY_RIGHT) }) { Text("▶") }
                }
                Button(onClick = { vm.key(KEY_DOWN) }) { Text("▼") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { vm.key(KEY_VOL_UP) }) { Text("VOL+") }
                Button(onClick = { vm.key(KEY_VOL_DOWN) }) { Text("VOL−") }
                Button(onClick = { vm.mute() }) { Text("MUTE") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { vm.key(KEY_CHANNEL_UP) }) { Text("CH+") }
                Button(onClick = { vm.key(KEY_CHANNEL_DOWN) }) { Text("CH−") }
                Button(onClick = { vm.key(KEY_PLAY) }) { Text("PLAY") }
                Button(onClick = { vm.key(KEY_PAUSE) }) { Text("PAUSE") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { vm.key(KEY_PREV) }) { Text("PREV") }
                Button(onClick = { vm.key(KEY_NEXT) }) { Text("NEXT") }
            }
        }

        SectionCard("Volume") {
            Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..100f)
            Button(onClick = { vm.volume(volume.toInt()) }) { Text("Set volume ${volume.toInt()}") }
        }

        SectionCard("Air mouse & keyboard") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mouseOn, onClick = {
                    mouseOn = !mouseOn
                    vm.mouseEnable(mouseOn)
                }, label = { Text(if (mouseOn) "Mouse ON" else "Mouse OFF") })
                Button(onClick = { vm.airClick() }) { Text("Click") }
            }
            Column(Modifier.fillMaxWidth().height(100.dp).padding(8.dp)) {
                var dx by remember { mutableFloatStateOf(0f) }
                var dy by remember { mutableFloatStateOf(0f) }
                Slider(value = dx, onValueChange = { dx = it }, valueRange = -50f..50f)
                Slider(value = dy, onValueChange = { dy = it }, valueRange = -50f..50f)
                Button(onClick = { vm.airmouse(dx, dy, 0) }) { Text("Move ($dx, $dy)") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Type on TV") },
                singleLine = true,
                keyboardActions = KeyboardActions(onSend = { vm.typeText(text) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.typeText(text) }, Modifier.fillMaxWidth()) { Text("Send text") }
        }

        SectionCard("App shortcuts & macros") {
            OutlinedTextField(
                value = macroPackage,
                onValueChange = { macroPackage = it },
                label = { Text("Package name (e.g. com.youtube...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.launch(macroPackage) }) { Text("Launch") }
                Button(onClick = { vm.volume(15); vm.launch(macroPackage) }) { Text("Movie mode") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.apps.take(20)) { app ->
                    FilterChip(
                        selected = app.packageName in vm.favorites(),
                        onClick = { vm.toggleFavorite(app.packageName) },
                        label = { Text(app.name) }
                    )
                }
            }
            Button(onClick = { vm.refreshApps() }) { Text("Refresh app list") }
        }
    }
}
