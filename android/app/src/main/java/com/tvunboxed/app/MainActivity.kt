package com.tvunboxed.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tvunboxed.app.core.Protocol
import com.tvunboxed.app.core.TvDevice
import com.tvunboxed.app.ui.CareScreen
import com.tvunboxed.app.ui.CastScreen
import com.tvunboxed.app.ui.FilesScreen
import com.tvunboxed.app.ui.MirrorScreen
import com.tvunboxed.app.ui.RemoteScreen
import com.tvunboxed.app.ui.StatusBanner
import com.tvunboxed.app.ui.TVunboxedTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            TVunboxedTheme {
                TVunboxedApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank() && text.contains("://")) {
                    vm.sendUrl(text)
                    return
                }
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    val name = uri.toString().substringAfterLast('/').ifBlank { "shared-file" }
                    vm.copyUriToFile(uri, name)?.let { vm.addToQueue(it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.forEach { uri ->
                    val name = uri.toString().substringAfterLast('/').ifBlank { "shared-file" }
                    vm.copyUriToFile(uri, name)?.let { vm.addToQueue(it) }
                }
            }
        }
    }
}

private data class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TVunboxedApp(vm: MainViewModel) {
    val navController = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    var manualDialog by remember { mutableStateOf(false) }

    val destinations = listOf(
        Dest("remote", "Remote", Icons.Filled.Home),
        Dest("cast", "Cast", Icons.Filled.PlayArrow),
        Dest("mirror", "Mirror", Icons.Filled.Settings),
        Dest("files", "Files", Icons.Filled.List),
        Dest("care", "TV Care", Icons.Filled.Info)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TVunboxed")
                        Text(
                            "${state.device?.name ?: "No TV"} · ${if (state.connected) "connected" else "offline"}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.scan() }) { Icon(Icons.Filled.Refresh, "Scan") }
                    Button(onClick = { manualDialog = true }) { Text("Connect") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label, Modifier.size(24.dp)) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatusBanner(state.status, state.busy)
            if (state.busy) {
                CircularProgressIndicator(Modifier.padding(8.dp).size(22.dp), strokeWidth = 2.dp)
            }
            Box(Modifier.weight(1f)) {
                NavHost(navController, startDestination = "remote") {
                    composable("remote") { RemoteScreen(vm) }
                    composable("cast") { CastScreen(vm) }
                    composable("mirror") { MirrorScreen(vm) }
                    composable("files") { FilesScreen(vm) }
                    composable("care") { CareScreen(vm) }
                }
            }
        }
    }

    if (manualDialog) {
        var ip by remember { mutableStateOf(state.device?.ip ?: "192.168.1.201") }
        var controlPort by remember { mutableStateOf(Protocol.CONTROL_PORT.toString()) }
        var httpPort by remember { mutableStateOf(Protocol.HTTP_PORT.toString()) }
        AlertDialog(
            onDismissRequest = { manualDialog = false },
            title = { Text("Connect to TV") },
            text = {
                Column {
                    OutlinedTextField(ip, { ip = it }, label = { Text("TV IP") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(controlPort, { controlPort = it }, label = { Text("Control port") }, singleLine = true)
                    OutlinedTextField(httpPort, { httpPort = it }, label = { Text("HTTP port") }, singleLine = true)
                    state.discovered.take(4).forEach { device: TvDevice ->
                        Button(onClick = {
                            vm.connect(device)
                            manualDialog = false
                        }) { Text("Connect to ${device.name} (${device.ip})") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.connectManual(ip, controlPort.toIntOrNull() ?: Protocol.CONTROL_PORT,
                        httpPort.toIntOrNull() ?: Protocol.HTTP_PORT)
                    manualDialog = false
                }) { Text("Connect") }
            },
            dismissButton = { Button(onClick = { manualDialog = false }) { Text("Cancel") } }
        )
    }
}

