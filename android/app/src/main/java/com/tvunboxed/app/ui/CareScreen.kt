package com.tvunboxed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
fun CareScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingUninstall by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCard("Device & diagnostics") {
            Text("Name: ${state.device?.name ?: "Unavailable"}")
            Text("IP: ${state.device?.ip ?: "Unavailable"}")
            Text("Model: ${state.device?.model ?: "Unavailable"}")
            Text("Serial: ${state.device?.serial ?: "Unavailable"}")
            Button(onClick = { vm.runDiagnostics() }) { Text("Run diagnostics") }
            Text(state.diagnostics)
        }

        SectionCard("Capability matrix") {
            LazyColumn {
                items(Capability.entries) { capability ->
                    Text("$capability = ${state.capabilities.status(capability)}", Modifier.fillMaxWidth())
                }
            }
        }

        SectionCard("App manager") {
            Button(onClick = { vm.refreshApps() }) { Text("Refresh apps") }
            LazyColumn {
                items(state.apps) { app ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(app.name)
                            Text("${app.packageName} · ${app.versionName} (${app.versionCode})")
                        }
                        FilterChip(
                            selected = app.packageName in vm.favorites(),
                            onClick = { vm.toggleFavorite(app.packageName) },
                            label = { Text("★") }
                        )
                        Button(onClick = { vm.launch(app.packageName) }) { Text("Open") }
                        Button(onClick = { pendingUninstall = app.packageName }) { Text("X") }
                    }
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
                Button(onClick = {
                    vm.uninstall(target)
                    pendingUninstall = null
                }) { Text("Uninstall") }
            },
            dismissButton = { Button(onClick = { pendingUninstall = null }) { Text("Cancel") } }
        )
    }
}

