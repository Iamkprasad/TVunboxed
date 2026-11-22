package com.tvunboxed.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
fun WatchScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("apps") }
    var url by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("0") }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.toString().substringAfterLast('/').ifBlank { "media" }
            vm.copyUriToFile(uri, name)?.let { vm.addToQueue(it) }
        }
    }
    val pickM3u = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.setStatus("Playlist selected: ${uri.lastPathSegment}")
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "apps", onClick = { tab = "apps" }, label = { Text("Apps") })
            FilterChip(selected = tab == "chan", onClick = { tab = "chan" }, label = { Text("Channels") })
            FilterChip(selected = tab == "iptv", onClick = { tab = "iptv" }, label = { Text("Playlist") })
        }

        when (tab) {
            "apps" -> {
                SectionCard("Apps on TV") {
                    Button(onClick = { vm.refreshApps() }) { Text("Refresh") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.apps.take(12).forEach { app ->
                            Button(onClick = { vm.launch(app.packageName) }) { Text(app.name) }
                        }
                    }
                }
                SectionCard("Send link to TV") {
                    OutlinedTextField(url, { url = it }, label = { Text("http://...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.sendUrl(url) }) { Text("Open on TV") }
                        Button(onClick = { vm.saveFile(url, "video/mp4") }) { Text("Save to TV") }
                    }
                }
            }
            "chan" -> {
                SectionCard("Free channels") {
                    listOf("Al Jazeera English", "DW English", "CGTN").forEach { name ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name)
                            Button(onClick = { vm.openLink("https://example.com/$name") }) { Text("Cast") }
                        }
                    }
                }
            }
            "iptv" -> {
                SectionCard("Load a playlist") {
                    Button(onClick = { pickM3u.launch("*/*") }, Modifier.fillMaxWidth()) { Text("Choose .m3u file") }
                    Button(onClick = { vm.setStatus("Sample playlist added") }, Modifier.fillMaxWidth()) { Text("Try sample playlist") }
                }
            }
        }

        SectionCard("Local media streaming") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickMedia.launch("video/*") }) { Text("Video") }
                Button(onClick = { pickMedia.launch("image/*") }) { Text("Photo") }
                Button(onClick = { pickMedia.launch("audio/*") }) { Text("Audio") }
            }
            LazyColumn { items(state.queue) { item -> Text("${item.name} — ${item.url}") } }
        }

        SectionCard("Playback controls") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { vm.media("play") }) { Text("Play") }
                Button(onClick = { vm.media("pause") }) { Text("Pause") }
                Button(onClick = { vm.media("stop") }) { Text("Stop") }
                Button(onClick = { vm.queryMedia("getPlayingInfo") }) { Text("Info") }
            }
            OutlinedTextField(position, { position = it }, label = { Text("Seek ms") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { position.toLongOrNull()?.let { vm.seek(it) } }) { Text("Seek") }
            Text(state.mediaStatus)
        }
    }
}