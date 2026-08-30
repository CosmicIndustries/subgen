package com.cosmicindustries.umbra.ui.dpi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ByeDpiSettingsScreen(viewModel: ByeDpiSettingsViewModel) {
    val wrapEnabled by viewModel.wrapEnabled.collectAsStateWithLifecycle()
    val udpFakeCount by viewModel.udpFakeCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("DPI bypass", style = MaterialTheme.typography.headlineSmall)
        Text(
            "byedpi wraps WireGuard's own connection to your VPN server, not " +
                "individual apps' traffic — this helps on networks that " +
                "specifically detect and throttle or block WireGuard itself. " +
                "Takes effect next time the tunnel starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wrap WireGuard via byedpi", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(checked = wrapEnabled, onCheckedChange = viewModel::setWrapEnabled)
        }

        if (wrapEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Fake UDP packets: $udpFakeCount", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Decoy datagrams sent before each real one, byedpi's UDP " +
                        "analogue of its TCP fake-packet technique (--udp-fake).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = udpFakeCount.toFloat(),
                    onValueChange = { viewModel.setUdpFakeCount(it.toInt()) },
                    valueRange = 0f..8f,
                    steps = 7,
                )
            }
        }
    }
}
