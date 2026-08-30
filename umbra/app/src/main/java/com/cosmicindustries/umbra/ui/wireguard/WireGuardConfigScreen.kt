package com.cosmicindustries.umbra.ui.wireguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WireGuardConfigScreen(viewModel: WireGuardConfigViewModel) {
    val rawConfig by viewModel.rawConfig.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("WireGuard config", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Paste a standard wg-quick [Interface]/[Peer] config. Per-app " +
                "routing is applied automatically from the App List's " +
                "\"WireGuard\" selections when the tunnel starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = rawConfig,
            onValueChange = viewModel::onTextChanged,
            modifier = Modifier.fillMaxWidth().height(360.dp),
            label = { Text("Config text") },
            isError = error != null,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (saved) {
            Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = viewModel::save) { Text("Save") }
    }
}
