package app.hushfocus.hush.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hushfocus.hush.R
import app.hushfocus.hush.ui.settings.ScreenHeader
import app.hushfocus.hush.ui.settings.SectionTitle

/**
 * Setup: the accessibility service and Android's "Allow restricted settings" step for
 * sideloaded apps (spec §7). Requests no permissions; those come with their features.
 */
@Composable
fun SetupScreen(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(stringResource(R.string.setup_title), onBack)

            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.setup_service_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(if (serviceEnabled) R.string.setup_service_on else R.string.setup_service_off),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(stringResource(R.string.setup_service_explain), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenAccessibilitySettings) {
                        Text(stringResource(R.string.setup_open_accessibility))
                    }
                }
            }

            SectionTitle(stringResource(R.string.setup_restricted_title))
            Text(
                stringResource(R.string.setup_restricted_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Step(1, stringResource(R.string.setup_restricted_step_1))
            Step(2, stringResource(R.string.setup_restricted_step_2))
            Step(3, stringResource(R.string.setup_restricted_step_3))
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.setup_open_app_info))
            }

            Text(
                stringResource(R.string.setup_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** A numbered step: the number in a filled circle, then the instruction. */
@Composable
private fun Step(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
