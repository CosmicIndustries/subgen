package com.cosmicindustries.umbra.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmicindustries.umbra.firewall.AppMode
import com.cosmicindustries.umbra.firewall.AppRule
import com.cosmicindustries.umbra.firewall.DebloatTier

@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { DebloatPresetRow(onPresetSelected = viewModel::applyDebloatPreset) }
        items(rules, key = { it.packageName }) { rule ->
            AppRuleRow(rule, onModeSelected = { viewModel.setMode(rule.packageName, it) })
            Divider()
        }
    }
}

/**
 * Bulk-blocks known bloat/telemetry packages by risk tier, sourced from the Universal
 * Android Debloater Next Generation project's list (see [DebloatTier]/[com.cosmicindustries.umbra.firewall.DebloatList]).
 * Each button is cumulative and additive — it only ever sets matching, currently-installed
 * apps to Blocked, never touching apps outside that list or downgrading an existing rule.
 */
@Composable
private fun DebloatPresetRow(onPresetSelected: (DebloatTier) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Block known bloatware", style = MaterialTheme.typography.titleSmall)
        Text(
            "Sets matching installed apps to Blocked, by risk tier (Universal Android Debloater project). " +
                "Apps not on that list are untouched; you can still change any app back by hand.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onPresetSelected(DebloatTier.RECOMMENDED) }) { Text("Recommended") }
            Button(onClick = { onPresetSelected(DebloatTier.ADVANCED) }) { Text("Advanced") }
            Button(onClick = { onPresetSelected(DebloatTier.EXPERT) }) { Text("Expert") }
        }
    }
    Divider()
}

@Composable
private fun AppRuleRow(rule: AppRule, onModeSelected: (AppMode) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.appName, style = MaterialTheme.typography.bodyLarge)
            Text(rule.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column {
            TextButton(onClick = { menuExpanded = true }) {
                Text(rule.mode.label())
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                AppMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label()) },
                        onClick = {
                            onModeSelected(mode)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun AppMode.label(): String = when (this) {
    AppMode.ALLOW_DIRECT -> "Direct"
    AppMode.VPN_WIREGUARD -> "WireGuard"
    AppMode.BLOCKED -> "Blocked"
}
