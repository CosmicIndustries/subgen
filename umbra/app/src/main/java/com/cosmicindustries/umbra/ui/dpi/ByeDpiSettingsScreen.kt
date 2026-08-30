package com.cosmicindustries.umbra.ui.dpi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmicindustries.umbra.dpi.ByeDpiConfig

@Composable
fun ByeDpiSettingsScreen(viewModel: ByeDpiSettingsViewModel) {
    val desyncMode by viewModel.desyncMode.collectAsStateWithLifecycle()
    val splitPosition by viewModel.splitPosition.collectAsStateWithLifecycle()
    val fakeSni by viewModel.fakeSni.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("DPI-bypass tuning", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Desync strategy byedpi applies to blocked/throttled TCP flows. " +
                "Takes effect next time the DPI-bypass tunnel starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Strategy", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ByeDpiConfig.DesyncMode.entries.filter { it != ByeDpiConfig.DesyncMode.NONE }.forEach { mode ->
                FilterChip(
                    selected = desyncMode == mode.name,
                    onClick = { viewModel.setDesyncMode(mode.name) },
                    label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }

        OutlinedTextField(
            value = splitPosition,
            onValueChange = viewModel::setSplitPosition,
            label = { Text("Split position") },
            supportingText = { Text("byedpi's pos_t syntax, e.g. \"2\" or \"1,midsld\"") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = fakeSni,
            onValueChange = viewModel::setFakeSni,
            label = { Text("Fake SNI (optional)") },
            supportingText = { Text("Only used in Fake mode") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
