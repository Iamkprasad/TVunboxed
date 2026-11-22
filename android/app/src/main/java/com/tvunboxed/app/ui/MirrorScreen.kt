package com.tvunboxed.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvunboxed.app.MainViewModel
import com.tvunboxed.app.core.Capability

@Composable
fun MirrorScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var interval by remember { mutableFloatStateOf(1000f) }
    var touchback by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { vm.stopFrameLoop() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCard("TV screen on phone") {
            Text("Endpoint: /remote/screencapture — status: ${state.capabilities.status(Capability.TV_SCREEN_CAPTURE)}")
            val frame = state.tvFrame
            if (frame != null) {
                val bitmap = remember(frame) {
                    BitmapFactory.decodeByteArray(frame, 0, frame.size)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .pointerInput(touchback) {
                                detectTapGestures { offset ->
                                    if (touchback) {
                                        vm.touchAt(
                                            offset.x / size.width * 1000f,
                                            offset.y / size.height * 1000f,
                                            mirror = true
                                        )
                                    }
                                }
                            }
                    ) {
                        Image(bitmap, contentDescription = "TV screen", contentScale = ContentScale.Fit)
                    }
                } else {
                    Text("Frame could not be decoded")
                }
            } else {
                Text("No frame yet. Tap refresh.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.captureFrame() }) { Text("Refresh") }
                Button(onClick = {
                    running = !running
                    if (running) vm.startFrameLoop() else vm.stopFrameLoop()
                }) { Text(if (running) "Stop live" else "Start live") }
                FilterChip(selected = touchback, onClick = { touchback = !touchback }, label = { Text("Touchback") })
            }
            Slider(
                value = interval,
                onValueChange = { interval = it },
                valueRange = 300f..5000f,
                onValueChangeFinished = {
                    vm.setFrameInterval(interval.toLong())
                    if (running) vm.startFrameLoop()
                }
            )
            Text("Refresh every ${interval.toInt()} ms")
        }

        SectionCard("Phone → TV mirroring") {
            Text("Full screen mirroring needs an optional TVunboxed receiver on the TV (MediaProjection + WebRTC). Not available through this control channel alone.")
            Text("Current status: ${state.capabilities.status(Capability.OPTIONAL_RECEIVER)}")
        }

        SectionCard("Camera & presentation") {
            Text("Camera streaming and annotation are planned after TV-view and streaming are validated on this TV.")
        }
    }
}
