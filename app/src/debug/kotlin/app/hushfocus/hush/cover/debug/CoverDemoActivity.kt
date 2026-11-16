package app.hushfocus.hush.cover.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.hushfocus.hush.R
import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.ui.theme.HushTheme
import kotlin.math.roundToInt

/**
 * Debug build only: draws a test cover over a box in this screen, never over another app.
 * Start with: adb shell am start -n app.hushfocus.hush/.cover.debug.CoverDemoActivity
 */
class CoverDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HushTheme { CoverDemoScreen() } }
    }
}

@Composable
private fun CoverDemoScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    var boxRect by remember { mutableStateOf<CoverRect?>(null) }
    var status by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    val serviceOff = stringResource(R.string.cover_demo_service_off)
    val shownAt = stringResource(R.string.cover_demo_shown_at)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.cover_demo_title), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val manager = ServiceDebugHooks.coverManager
                    val rect = boxRect
                    status = if (manager == null || rect == null) {
                        serviceOff
                    } else {
                        manager.show(context.packageName, CoverKind.REELS_TAB, rect)
                        shownAt.format(rect.left, rect.top, rect.right, rect.bottom)
                    }
                }) { Text(stringResource(R.string.cover_demo_show)) }
                OutlinedButton(onClick = { ServiceDebugHooks.coverManager?.hideAll() }) {
                    Text(stringResource(R.string.cover_demo_hide_all))
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.cover_demo_keyboard)) },
                singleLine = true,
                // Enter closes only the keyboard (Back would also close this screen).
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            // A scrollable list below the box, to check the cover doesn't flicker while scrolling.
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(40) { index ->
                    Text(
                        stringResource(R.string.cover_demo_filler, index + 1),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            // The target sits at the bottom, like a tab bar, so the keyboard overlaps it.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 220.dp, height = 72.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        val window = IntArray(2).also(view::getLocationOnScreen)
                        boxRect = CoverRect(
                            window[0] + bounds.left.roundToInt(),
                            window[1] + bounds.top.roundToInt(),
                            window[0] + bounds.right.roundToInt(),
                            window[1] + bounds.bottom.roundToInt(),
                        )
                    },
            ) { Text(stringResource(R.string.cover_demo_target)) }
        }
    }
}
