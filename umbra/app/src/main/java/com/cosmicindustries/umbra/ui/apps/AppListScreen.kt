package com.cosmicindustries.umbra.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(rules, key = { it.packageName }) { rule ->
            AppRuleRow(rule, onModeSelected = { viewModel.setMode(rule.packageName, it) })
            Divider()
        }
    }
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
