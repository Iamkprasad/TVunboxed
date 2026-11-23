package com.tvunboxed.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tvunboxed.app.core.Protocol
import com.tvunboxed.app.core.TvDevice
import com.tvunboxed.app.ui.CapabilityChips2
import com.tvunboxed.app.ui.EmptyState2
import com.tvunboxed.app.ui.HomeScreen
import com.tvunboxed.app.ui.RemoteScreen
import com.tvunboxed.app.ui.StatusBanner2
import com.tvunboxed.app.ui.TvButton
import com.tvunboxed.app.ui.TvButtonVariant
import com.tvunboxed.app.ui.TvCard
import com.tvunboxed.app.ui.TVunboxedTheme
import com.tvunboxed.app.ui.TvIcon
import com.tvunboxed.app.ui.TvIconButton
import com.tvunboxed.app.ui.TvMono
import com.tvunboxed.app.ui.TvSans
import com.tvunboxed.app.ui.TvSerif
import com.tvunboxed.app.ui.TvTextField
import com.tvunboxed.app.ui.FilesScreen
import com.tvunboxed.app.ui.WatchScreen
import com.tvunboxed.app.ui.ToolsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { com.tvunboxed.app.web.WebViewScreen(vm, onBackPressedDispatcher, this) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank() && text.contains("://")) { vm.sendUrl(text); return }
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

@Composable
private fun TVunboxedApp(vm: MainViewModel) {
    val navController = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    var manualDialog by remember { mutableStateOf(false) }

    val destinations = listOf("home" to "Home", "files" to "Files", "watch" to "Watch", "tools" to "Tools")

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0F1A))) {
        // App bar
        Box(Modifier.fillMaxWidth().background(Color(0xFF16182B))) {
            Row(Modifier.fillMaxWidth().padding(9.dp, 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                TvIcon("tv", size = 38.dp, tint = Color.Unspecified)
                Column {
                    Text("TVunboxed", fontFamily = TvSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFEDEFFC))
                    Text("mixtape edition", fontFamily = TvMono, fontSize = 11.sp, color = Color(0xFFA6ACD6))
                }
                Spacer(Modifier.weight(1f))
                // TV chip
                Row(Modifier.height(42.dp).background(Color(0xFF1F2238)).border(2.dp, Color(0xFF636889)).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(9.dp).background(Color(0xFFC5E17A)))
                    Text(state.device?.name ?: "No TV", fontFamily = TvMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFEDEFFC))
                    TvIcon("down", size = 14.dp, tint = Color(0xFFA6ACD6))
                }
                TvIconButton(onClick = { manualDialog = true }) { TvIcon("gear", size = 20.dp, tint = Color(0xFFEDEFFC)) }
            }
            // foil
            val t = rememberInfiniteTransition(label = "foil")
            val off by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "foiloff")
            Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter).background(
                Brush.horizontalGradient(listOf(Color(0xFFCCCFFA), Color(0xFFFFBFD9), Color(0xFFB2F0ED), Color(0xFFC5E17A), Color(0xFFADDBFF), Color(0xFFD5CDFF), Color(0xFFCCCFFA)))
            ))
        }
        StatusBanner2(state.status, state.busy)
        if (state.busy) CircularProgressIndicator(Modifier.padding(8.dp).size(22.dp), strokeWidth = 2.dp)
        Box(Modifier.weight(1f)) {
            NavHost(navController, startDestination = "home") {
                composable("home") { HomeScreen(vm, onOpenRemote = { navController.navigate("remote") }, onOpenAir = { navController.navigate("remote") }) }
                composable("files") { FilesScreen(vm) }
                composable("watch") { WatchScreen(vm) }
                composable("tools") { ToolsScreen(vm) }
                composable("remote") { RemoteScreen(vm) }
            }
        }
        // Bottom nav
        Row(Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF16182B)).border(width = 1.dp, color = Color(0xFF2C3048)), verticalAlignment = Alignment.CenterVertically) {
            destinations.forEach { (route, label) ->
                val selected = current?.hierarchy?.any { it.route == route } == true
                Column(Modifier.weight(1f).clickable { navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when (route) { "home" -> "home"; "files" -> "folder"; "watch" -> "tv"; else -> "tools" }
                    TvIcon(icon, size = 22.dp, tint = if (selected) Color(0xFF2A1159) else Color(0xFFA6ACD6))
                    Text(label, fontFamily = TvSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) Color(0xFF2A1159) else Color(0xFFA6ACD6))
                }
            }
        }
    }

    if (manualDialog) {
        var ip by remember { mutableStateOf(state.device?.ip ?: "192.168.1.201") }
        var controlPort by remember { mutableStateOf(Protocol.CONTROL_PORT.toString()) }
        var httpPort by remember { mutableStateOf(Protocol.HTTP_PORT.toString()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { manualDialog = false },
            title = { Text("Connect to TV") },
            text = {
                Column {
                    TvTextField(ip, { ip = it }, "TV IP")
                    TvTextField(controlPort, { controlPort = it }, "Control port")
                    TvTextField(httpPort, { httpPort = it }, "HTTP port")
                }
            },
            confirmButton = { TvButton(onClick = { vm.connectManual(ip, controlPort.toIntOrNull() ?: Protocol.CONTROL_PORT, httpPort.toIntOrNull() ?: Protocol.HTTP_PORT); manualDialog = false }) { Text("Connect") } },
            dismissButton = { TvButton(onClick = { manualDialog = false }, variant = TvButtonVariant.Default) { Text("Cancel") } }
        )
    }
}