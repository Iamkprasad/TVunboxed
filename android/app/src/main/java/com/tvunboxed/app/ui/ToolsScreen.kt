package com.tvunboxed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.tvunboxed.app.core.Capability

@Composable
fun ToolsScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("send") }
    var url by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var un by remember { mutableStateOf("") }
    var pendingUninstall by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "send", onClick = { tab = "send" }, label = { Text("Send") })
            FilterChip(selected = tab == "care", onClick = { tab = "care" }, label = { Text("TV Care") })
            FilterChip(selected = tab == "danger", onClick = { tab = "danger" }, label = { Text("Danger") })
        }

        when (tab) {
            "send" -> {
                SectionCard("Open a link on TV") {
                    OutlinedTextField(url, { url = it }, label = { Text("https://...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.sendUrl(url) }, Modifier.fillMaxWidth()) { Text("Open") }
                }
                SectionCard("Launch an app by package") {
                    OutlinedTextField(pkg, { pkg = it }, label = { Text("com.example.app") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.launch(pkg) }) { Text("Launch") }
                        Button(onClick = { vm.volume(15); vm.launch(pkg) }) { Text("Movie mode") }
                    }
                }
                SectionCard("Type on TV") {
                    Text("Use your phone keyboard to fill search boxes and logins on the TV.")
                    Button(onClick = { /* handled by remote overlay */ }, Modifier.fillMaxWidth()) { Text("Open keyboard (Remote → Keys)") }
                }
            }
            "care" -> {
                SectionCard("Device & diagnostics") {
                    Text("Name: ${state.device?.name ?: "Unavailable"}")
                    Text("IP: ${state.device?.ip ?: "Unavailable"}")
                    Text("Model: ${state.device?.model ?: "Unavailable"}")
                    Text("Serial: ${state.device?.serial ?: "Unavailable"}")
                    Button(onClick = { vm.runDiagnostics() }) { Text("Run diagnostics") }
                    Text(state.diagnostics)
                }
                SectionCard("Capability matrix") {
                    state.capabilities.toMap().toSortedMap().forEach { (k, v) ->
                        Text("$k = $v", Modifier.fillMaxWidth())
                    }
                }
                SectionCard("App manager") {
                    Button(onClick = { vm.refreshApps() }) { Text("Refresh apps") }
                    state.apps.take(30).forEach { app ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(app.name)
                                Text("${app.packageName} · ${app.versionName}")
                            }
                            Button(onClick = { vm.launch(app.packageName) }) { Text("Open") }
                            Button(onClick = { pendingUninstall = app.packageName }) { Text("X") }
                        }
                    }
                }
            }
            "danger" -> {
                SectionCard("Uninstall an app") {
                    OutlinedTextField(un, { un = it }, label = { Text("package name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (un.isNotBlank()) pendingUninstall = un.trim()
                    }, Modifier.fillMaxWidth()) { Text("Uninstall") }
                }
            }
        }
    }

    pendingUninstall?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text("Uninstall on TV?") },
            text = { Text("Remove $target from the TV? This is destructive and cannot be undone.") },
            confirmButton = {
                Button(onClick = { vm.uninstall(target); pendingUninstall = null }) { Text("Uninstall") }
            },
            dismissButton = { Button(onClick = { pendingUninstall = null }) { Text("Cancel") } }
        )
    }
}