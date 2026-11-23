package com.tvunboxed.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvunboxed.app.MainViewModel
import com.tvunboxed.app.core.Protocol

@Composable
fun FilesScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var path by remember { mutableStateOf(state.davPath) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var fType by remember { mutableStateOf("all") }
    var fQ by remember { mutableStateOf("") }
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

    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // page head
        Text("Files", fontFamily = TvSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFEDEFFC))
        Text("Send anything from your phone to the TV.", fontFamily = TvSans, fontSize = 13.sp, color = Color(0xFFA6ACD6))

        TvCard {
            // crumbs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(onClick = { }) { TvIcon("up", size = 20.dp, tint = Color(0xFFEDEFFC)) }
                Box(Modifier.weight(1f).height(42.dp).background(Color(0xFF0B0C16)).border(2.dp, Color(0xFF636889)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvIcon("folder", size = 14.dp, tint = Color(0xFFA6ACD6))
                        Text("Phone / Download", fontFamily = TvMono, fontSize = 13.sp, color = Color(0xFFEDEFFC))
                    }
                }
                TvIconButton(onClick = { vm.browse(path) }) { TvIcon("refresh", size = 20.dp, tint = Color(0xFFEDEFFC)) }
            }
            Spacer(Modifier.height(8.dp))
            // search
            TvTextField(value = fQ, onValueChange = { fQ = it }, label = "Search files", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            // chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("all" to "All", "video" to "Video", "image" to "Images", "audio" to "Audio", "doc" to "Docs").forEach { (t, l) ->
                    TvChip(label = l, active = fType == t, onClick = { fType = t })
                }
            }
            Spacer(Modifier.height(8.dp))
            TvButton(onClick = { vm.browse(path) }, modifier = Modifier.fillMaxWidth()) { Text("Browse") }
            LazyColumn {
                items(state.davEntries.filter { fType == "all" || it.name.endsWith(fType) || true }) { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(42.dp).background(if (entry.isDirectory) Color(0xFFD5CDFF) else Color(0xFFADDBFF)).border(2.dp, Color(0xFF636889)), contentAlignment = Alignment.Center) {
                                TvIcon(if (entry.isDirectory) "folder" else "doc", size = 20.dp, tint = Color(0xFF1C1C1C))
                            }
                            Column {
                                Text(entry.name, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFEDEFFC), maxLines = 1)
                                Text(if (entry.isDirectory) "folder" else "file", fontFamily = TvMono, fontSize = 12.sp, color = Color(0xFFA6ACD6))
                            }
                        }
                        if (!entry.isDirectory) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TvButton(onClick = { vm.downloadFromTv(entry.path) }) { Text("Get") }
                                TvButton(onClick = { pendingDelete = entry.path }, variant = TvButtonVariant.Danger) { Text("Del") }
                            }
                        } else {
                            TvButton(onClick = { path = entry.path; vm.browse(entry.path) }) { Text("Open") }
                        }
                    }
                }
            }
        }
        TvCard(title = "Transfer") {
            TvButton(onClick = { pickFile.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { TvIcon("upload", size = 20.dp, tint = Color(0xFF2A1159)); Text("Upload file from phone to current TV folder") }
            TvButton(onClick = { vm.ensureMediaServer() }, modifier = Modifier.fillMaxWidth(), variant = TvButtonVariant.Blue) { Text("Start phone media server (used by Cast)") }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete on TV?") },
            text = { Text("Permanently delete $target? This cannot be undone.") },
            confirmButton = { TvButton(onClick = { vm.deleteOnTv(target); pendingDelete = null }, variant = TvButtonVariant.Danger) { Text("Delete") } },
            dismissButton = { TvButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}