package com.tvunboxed.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvunboxed.app.MainViewModel

@Composable
fun CastScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("0") }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.toString().substringAfterLast('/').ifBlank { "media" }
            vm.copyUriToFile(uri, name)?.let { vm.addToQueue(it) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCard("Send link to TV") {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("http://... or YouTube link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.sendUrl(url) }) { Text("Open on TV") }
                Button(onClick = { vm.saveFile(url, "video/mp4") }) { Text("Save to TV") }
            }
        }

        SectionCard("Local media streaming") {
            Text("Files are served from this phone over Wi-Fi and opened on the TV. COPY TO TV uses WebDAV instead.")
            Button(onClick = { pickMedia.launch("video/*") }, Modifier.fillMaxWidth()) {
                Text("Choose video from phone")
            }
            Button(onClick = { pickMedia.launch("image/*") }, Modifier.fillMaxWidth()) {
                Text("Choose photo from phone")
            }
            Button(onClick = { pickMedia.launch("audio/*") }, Modifier.fillMaxWidth()) {
                Text("Choose audio from phone")
            }
            LazyColumn {
                items(state.queue) { item ->
                    Text("${item.name} — ${item.url}", Modifier.fillMaxWidth())
                }
            }
        }

        SectionCard("Playback controls") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { vm.media("play") }) { Text("Play") }
                Button(onClick = { vm.media("pause") }) { Text("Pause") }
                Button(onClick = { vm.media("stop") }) { Text("Stop") }
                Button(onClick = { vm.queryMedia("getPlayingInfo") }) { Text("Info") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { vm.queryMedia("getCurrentPosition") }) { Text("Position") }
                Button(onClick = { vm.queryMedia("getMediaDuration") }) { Text("Duration") }
                Button(onClick = { vm.queryMedia("getCurrentVolume") }) { Text("Volume?") }
            }
            OutlinedTextField(
                value = position,
                onValueChange = { position = it },
                label = { Text("Seek to milliseconds") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { position.toLongOrNull()?.let { vm.seek(it) } }) { Text("Seek") }
            Text(state.mediaStatus)
        }

        SectionCard("History") {
            LazyColumn {
                items(state.history) { item -> Text(item, Modifier.fillMaxWidth()) }
            }
        }
    }
}
