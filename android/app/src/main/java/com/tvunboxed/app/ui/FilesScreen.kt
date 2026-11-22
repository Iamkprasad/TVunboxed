package com.tvunboxed.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.tvunboxed.app.core.Protocol

@Composable
fun FilesScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var path by remember { mutableStateOf(state.davPath) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.toString().substringAfterLast('/').ifBlank { "upload.bin" }
            val bytes = vm.copyUriToFile(uri, name)?.readBytes()
            if (bytes != null) {
                val target = state.davPath.trimEnd('/') + "/" + name
                vm.uploadToTv(target, bytes, "application/octet-stream")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCard("TV file manager (WebDAV :${Protocol.HTTP_PORT})") {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Path on TV") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.browse(path) }) { Text("Browse") }
                Button(onClick = { vm.browse("/sdcard/") }) { Text("SD card") }
                Button(onClick = { vm.browse("/") }) { Text("Root (read-only)") }
            }
            LazyColumn {
                items(state.davEntries) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.isDirectory) {
                                    path = entry.path
                                    vm.browse(entry.path)
                                }
                            }
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${if (entry.isDirectory) "DIR " else "FILE"} ${entry.name}")
                        if (!entry.isDirectory) {
                            Row {
                                Button(onClick = { vm.downloadFromTv(entry.path) }) { Text("Get") }
                                Button(onClick = { pendingDelete = entry.path }) { Text("Del") }
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Transfer") {
            Button(onClick = { pickFile.launch("*/*") }, Modifier.fillMaxWidth()) {
                Text("Upload file from phone to current TV folder")
            }
            Button(onClick = { vm.ensureMediaServer() }, Modifier.fillMaxWidth()) {
                Text("Start phone media server (used by Cast)")
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete on TV?") },
            text = { Text("Permanently delete $target? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteOnTv(target)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { Button(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}
