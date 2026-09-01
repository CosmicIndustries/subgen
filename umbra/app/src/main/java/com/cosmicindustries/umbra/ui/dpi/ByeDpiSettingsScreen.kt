package com.cosmicindustries.umbra.ui.dpi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ByeDpiSettingsScreen(viewModel: ByeDpiSettingsViewModel) {
    val wrapEnabled by viewModel.wrapEnabled.collectAsStateWithLifecycle()
    val udpFakeCount by viewModel.udpFakeCount.collectAsStateWithLifecycle()
    val fakeTtl by viewModel.fakeTtl.collectAsStateWithLifecycle()
    val customFakeData by viewModel.customFakeData.collectAsStateWithLifecycle()
    val scriptMode by viewModel.scriptMode.collectAsStateWithLifecycle()
    val rawArgs by viewModel.rawArgs.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced: full byedpi script", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Paste byedpi's own CLI arguments directly instead of using " +
                            "the sliders below — its full option set, not just the " +
                            "UDP-relevant subset Umbra exposes as simple controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = scriptMode, onCheckedChange = viewModel::setScriptMode)
            }

            if (scriptMode) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Any listen address flags (-i/--ip, -p/--port) are ignored — " +
                            "Umbra's own SOCKS5 client always connects to its fixed " +
                            "local listener, so those can't be changed here. " +
                            "Example: --fake -1 --ttl 8 --fake-data ':GET /...'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = rawArgs,
                        onValueChange = viewModel::setRawArgs,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        label = { Text("byedpi arguments") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            } else {
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

                if (udpFakeCount > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Decoy TTL: $fakeTtl", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "The one value worth tuning per-network (byedpi --ttl, " +
                                "default 8): pick it low enough that the decoy expires " +
                                "before reaching your real VPN server, but high enough " +
                                "that whatever's inspecting traffic on the way still " +
                                "sees it. Too high and it's indistinguishable from a " +
                                "real packet; too low and it never gets far enough to " +
                                "matter. If wrapping seems to make no difference, try " +
                                "values between 3 and 12.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = fakeTtl.toFloat(),
                            onValueChange = { viewModel.setFakeTtl(it.toInt()) },
                            valueRange = 1f..32f,
                            steps = 30,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Custom decoy payload (advanced)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Overrides byedpi's built-in decoy bytes (--fake-data). " +
                                "Leave blank to use byedpi's default. Only the content " +
                                "changes — length and send count are unaffected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = customFakeData,
                            onValueChange = viewModel::setCustomFakeData,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Decoy bytes (optional)") },
                        )
                    }
                }
            }
        }

        if (saved) {
            Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = viewModel::save) { Text("Save") }
    }
}
