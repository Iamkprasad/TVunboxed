package com.tvunboxed.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun RemoteScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf("remote") }
    var mouseOn by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var vol by remember { mutableFloatStateOf(20f) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "remote", onClick = { mode = "remote" }, label = { Text("Remote") })
            FilterChip(selected = mode == "air", onClick = { mode = "air" }, label = { Text("Air mouse") })
            FilterChip(selected = mode == "keys", onClick = { mode = "keys" }, label = { Text("Keys") })
        }

        when (mode) {
            "remote" -> {
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
                            Button(onClick = { vm.key(KEY_ENTER) }) { Text("OK") }
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
                        Button(onClick = { vm.media("play") }) { Text("PLAY") }
                        Button(onClick = { vm.media("pause") }) { Text("PAUSE") }
                    }
                }
                SectionCard("Volume") {
                    Slider(value = vol, onValueChange = { vol = it }, valueRange = 0f..100f)
                    Button(onClick = { vm.volume(vol.toInt()) }) { Text("Set volume ${vol.toInt()}") }
                }
            }
            "air" -> {
                SectionCard("Air mouse") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mouseOn, onClick = {
                            mouseOn = !mouseOn
                            vm.mouseEnable(mouseOn)
                        }, label = { Text(if (mouseOn) "Mouse ON" else "Mouse OFF") })
                        Button(onClick = { vm.airClick() }) { Text("Click") }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color(0xFF0B0C16))
                            .pointerInput(mouseOn) {
                                if (!mouseOn) return@pointerInput
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    vm.airmouse(dragAmount.x, dragAmount.y, 0)
                                }
                            }
                            .pointerInput(mouseOn) {
                                if (!mouseOn) return@pointerInput
                                detectTapGestures(onTap = { vm.airClick() })
                            }
                    ) {
                        Text("Touch pad", Modifier.align(Alignment.Center), color = Color.White)
                    }
                }
            }
            "keys" -> {
                SectionCard("Type on TV") {
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
            }
        }
    }
}