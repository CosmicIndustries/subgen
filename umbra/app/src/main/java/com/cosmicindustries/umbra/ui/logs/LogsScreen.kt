package com.cosmicindustries.umbra.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmicindustries.umbra.logging.ConnectionEvent
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val exportedFile by viewModel.exportedFile.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = viewModel::exportCsv) { Text("Export CSV") }
            TextButton(onClick = viewModel::clearAll) { Text("Clear") }
        }
        exportedFile?.let {
            Text(
                "Saved to ${it.absolutePath}",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn {
            items(events, key = { it.id }) { event ->
                LogRow(event)
                Divider()
            }
        }
    }
}

@Composable
private fun LogRow(event: ConnectionEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(timeFormat.format(event.timestampMillis), style = MaterialTheme.typography.bodySmall)
            Text(event.engine.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (event.blocked) {
                Text("BLOCKED", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(event.packageName, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${event.destHost ?: "?"}:${event.destPort} (${event.protocol})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
