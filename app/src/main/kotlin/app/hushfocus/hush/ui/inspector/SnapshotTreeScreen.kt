package app.hushfocus.hush.ui.inspector

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hushfocus.hush.R
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.NodeInfo
import app.hushfocus.hush.inspector.model.ScreenSnapshot

/** A snapshot's windows and node tree, one indented line per node. */
@Composable
fun SnapshotTreeScreen(snapshot: ScreenSnapshot, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val horizontal = rememberScrollState()
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .horizontalScroll(horizontal),
        ) {
            item {
                Column(modifier = Modifier.padding(8.dp)) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.inspector_back)) }
                    Text("${snapshot.packageName} · ${snapshot.trigger}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.inspector_caps_hit, snapshot.nodeCapHit, snapshot.depthCapHit),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    snapshot.windows.forEach { window ->
                        MonoLine(
                            "window ${window.type} layer=${window.layer} active=${window.active} " +
                                "focused=${window.focused} ${window.bounds.format()} ${window.title.orEmpty()}",
                        )
                    }
                }
            }
            items(snapshot.nodes) { node ->
                MonoLine(nodeLine(node), Modifier.padding(start = (8 + node.depth * 8).dp))
            }
        }
    }
}

@Composable
private fun MonoLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, softWrap = false, modifier = modifier)
}

private fun nodeLine(node: NodeInfo): String = buildList {
    add(node.viewId?.substringAfter(":id/")?.let { "#$it" } ?: "-")
    add(node.className?.substringAfterLast('.') ?: "?")
    add(node.bounds.format())
    if (node.flags.isNotEmpty()) add(node.flags.joinToString(",") { it.key })
    node.text?.let { add("text=$it") }
    node.contentDescription?.let { add("desc=$it") }
    node.hintText?.let { add("hint=$it") }
    node.paneTitle?.let { add("pane=$it") }
    node.stateDescription?.let { add("state=$it") }
    node.collectionInfo?.let { add("list ${it.rows}x${it.cols}") }
    node.collectionItemInfo?.let { add("item ${it.rowIndex},${it.colIndex}") }
}.joinToString(" · ")

private fun Bounds.format() = "[$left,$top,$right,$bottom]"
