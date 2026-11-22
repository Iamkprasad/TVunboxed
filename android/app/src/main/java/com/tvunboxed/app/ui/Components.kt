package com.tvunboxed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvunboxed.app.core.Capability
import com.tvunboxed.app.core.CapabilitySet
import com.tvunboxed.app.core.CapabilityStatus

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun StatusBanner(status: String, busy: Boolean) {
    Text(
        text = if (busy) "Working… $status" else status,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
fun CapabilityChips(capabilities: CapabilitySet) {
    val shown = listOf(
        Capability.VENDOR_CONTROL,
        Capability.WEBDAV,
        Capability.MEDIA_CONTROL,
        Capability.TV_SCREEN_CAPTURE,
        Capability.DLNA_RENDERER,
        Capability.GOOGLE_CAST
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { capability ->
            val status = capabilities.status(capability)
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("${capability.name}: ${status.name}") },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = when (status) {
                        CapabilityStatus.SUPPORTED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            )
        }
    }
}
