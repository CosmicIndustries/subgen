package com.cosmicindustries.umbra.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmicindustries.umbra.firewall.ShizukuStatus

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    requestVpnConsent: (onGranted: () -> Unit) -> Unit,
) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Umbra", style = MaterialTheme.typography.headlineMedium)
        Text(
            "WireGuard tunnel, optionally wrapped by byedpi's DPI evasion — configure on the WireGuard and DPI Bypass tabs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Tunnel", style = MaterialTheme.typography.titleMedium)
                        Text(if (isRunning) "Running" else "Stopped", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = {
                    if (isRunning) {
                        viewModel.stop(context)
                    } else {
                        requestVpnConsent { viewModel.start(context) }
                    }
                }) { Text(if (isRunning) "Stop" else "Start") }
            }
        }
        lastError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shizuku firewall", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (shizukuStatus) {
                        ShizukuStatus.NOT_RUNNING -> "Shizuku isn't running. Hard-blocked apps aren't enforced until it is."
                        ShizukuStatus.PERMISSION_DENIED -> "Shizuku is running but Umbra hasn't been granted permission."
                        ShizukuStatus.PERMISSION_GRANTED -> "Active — blocked apps are enforced at the OS level."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (shizukuStatus == ShizukuStatus.PERMISSION_DENIED) {
                    OutlinedButton(onClick = { viewModel.requestShizukuPermission() }) {
                        Text("Grant permission")
                    }
                }
            }
        }
    }
}
