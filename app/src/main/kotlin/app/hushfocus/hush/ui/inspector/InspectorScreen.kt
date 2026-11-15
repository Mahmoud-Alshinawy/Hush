package app.hushfocus.hush.ui.inspector

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hushfocus.hush.R
import app.hushfocus.hush.inspector.Inspector
import app.hushfocus.hush.inspector.InspectorCounters
import app.hushfocus.hush.inspector.InspectorSettings
import app.hushfocus.hush.inspector.model.InspectorEntry
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.ScrollRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Developer screen for the Inspector: service status, on/off switch, counters, the
 * captured entries, export and clear. Material defaults only — no visual design yet.
 */
@Composable
fun InspectorScreen(
    serviceEnabled: Boolean,
    resumed: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Inspector.store(context) }
    val settings = remember { InspectorSettings(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val inspectorOn by settings.enabled.collectAsState(initial = false)
    val version by store.version.collectAsState()
    val supportedEvents by InspectorCounters.supportedEvents.collectAsState()
    val treeReads by InspectorCounters.treeReads.collectAsState()
    val saved by InspectorCounters.saved.collectAsState()

    var entries by remember { mutableStateOf(emptyList<InspectorEntry>()) }
    var selected by remember { mutableStateOf<ScreenSnapshot?>(null) }

    // Reload only while the screen is in front, so background captures don't re-read the log.
    LaunchedEffect(version, resumed) {
        if (resumed) entries = withContext(Dispatchers.IO) { store.readAll().asReversed() }
    }

    val resources = LocalResources.current
    val exportFailedMessage = stringResource(R.string.inspector_export_failed)
    val clearedMessage = stringResource(R.string.inspector_cleared)
    val undoLabel = stringResource(R.string.inspector_undo)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { store.exportTo(it, System.currentTimeMillis()) }
                }.getOrNull()
            }
            snackbarHostState.showSnackbar(
                if (count != null) {
                    resources.getQuantityString(R.plurals.inspector_exported, count, count)
                } else {
                    exportFailedMessage
                },
            )
        }
    }

    selected?.let { snapshot ->
        SnapshotTreeScreen(snapshot = snapshot, onBack = { selected = null })
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.inspector_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(
                            if (serviceEnabled) R.string.inspector_service_on else R.string.inspector_service_off,
                        ),
                    )
                    OutlinedButton(onClick = onOpenAccessibilitySettings) {
                        Text(stringResource(R.string.inspector_open_accessibility_settings))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.inspector_switch_label), modifier = Modifier.weight(1f))
                        Switch(
                            checked = inspectorOn,
                            onCheckedChange = { on -> scope.launch { settings.setEnabled(on) } },
                        )
                    }
                    Text(
                        stringResource(R.string.inspector_counters, supportedEvents, treeReads, saved),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(pluralStringResource(R.plurals.inspector_entry_count, entries.size, entries.size))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("hush-inspector-${fileStamp()}.json") }) {
                            Text(stringResource(R.string.inspector_export))
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { store.clear() }
                                val result = snackbarHostState.showSnackbar(
                                    message = clearedMessage,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Long,
                                )
                                withContext(Dispatchers.IO) {
                                    if (result == SnackbarResult.ActionPerformed) store.undoClear() else store.confirmClear()
                                }
                            }
                        }) {
                            Text(stringResource(R.string.inspector_clear))
                        }
                    }
                }
                HorizontalDivider()
            }
            items(entries) { entry ->
                EntryRow(entry = entry, onOpen = { if (entry is ScreenSnapshot) selected = entry })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EntryRow(entry: InspectorEntry, onOpen: () -> Unit) {
    val time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.timeMs))
    val app = entry.packageName.substringAfterLast('.')
    val text = when (entry) {
        is ScreenSnapshot -> stringResource(
            R.string.inspector_snapshot_row,
            time,
            app,
            (entry.windows.firstOrNull { it.active } ?: entry.windows.firstOrNull())?.type ?: "-",
            pluralStringResource(R.plurals.inspector_node_count, entry.nodes.size, entry.nodes.size),
        )

        is ScrollRecord -> stringResource(
            R.string.inspector_scroll_row,
            time,
            app,
            entry.events,
            entry.sourceId?.substringAfter(":id/") ?: entry.sourceClass.orEmpty(),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry is ScreenSnapshot, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun fileStamp(): String =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault()).format(Instant.now())
