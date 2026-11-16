package app.hushfocus.hush.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hushfocus.hush.R
import app.hushfocus.hush.core.AppId
import app.hushfocus.hush.settings.HomeStatus
import app.hushfocus.hush.settings.HushSettingsRepository
import app.hushfocus.hush.settings.SettingsRules
import app.hushfocus.hush.ui.settings.ScreenHeader
import app.hushfocus.hush.ui.settings.SectionTitle
import app.hushfocus.hush.ui.settings.SwitchRow
import kotlinx.coroutines.launch

/** Hush's first screen: on/off, status, the supported apps and Advanced. */
@Composable
fun HomeScreen(
    repository: HushSettingsRepository,
    serviceEnabled: Boolean,
    onOpenSetup: () -> Unit,
    onOpenInstagram: () -> Unit,
    onOpenInspector: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val global by repository.globalSettings.collectAsState(initial = null)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(stringResource(R.string.app_name))
            val hushEnabled = global?.hushEnabled ?: return@Column

            SwitchRow(
                title = stringResource(R.string.home_hush_switch),
                checked = hushEnabled,
                onCheckedChange = { enabled -> scope.launch { repository.setHushEnabled(enabled) } },
            )
            StatusCard(SettingsRules.homeStatus(hushEnabled, serviceEnabled), onOpenSetup)

            SectionTitle(stringResource(R.string.home_apps_title))
            AppId.entries.forEach { app ->
                AppRow(
                    name = stringResource(app.nameRes()),
                    onClick = if (app == AppId.INSTAGRAM) onOpenInstagram else null,
                )
            }

            SectionTitle(stringResource(R.string.home_advanced_title))
            ClickableRow(
                title = stringResource(R.string.home_inspector),
                summary = stringResource(R.string.home_inspector_summary),
                onClick = onOpenInspector,
            )
        }
    }
}

@Composable
private fun StatusCard(status: HomeStatus, onOpenSetup: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    when (status) {
                        HomeStatus.RUNNING -> R.string.home_status_running
                        HomeStatus.HUSH_OFF -> R.string.home_status_hush_off
                        HomeStatus.SERVICE_OFF -> R.string.home_status_service_off
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (status == HomeStatus.SERVICE_OFF) {
                Text(
                    stringResource(R.string.home_status_service_off_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Button(onClick = onOpenSetup) { Text(stringResource(R.string.home_set_up)) }
            }
        }
    }
}

/** A supported app; apps without a page yet are shown disabled as "Coming soon". */
@Composable
private fun AppRow(name: String, onClick: (() -> Unit)?) {
    if (onClick != null) {
        ClickableRow(title = name, summary = null, onClick = onClick)
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.home_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClickableRow(title: String, summary: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@StringRes
private fun AppId.nameRes(): Int = when (this) {
    AppId.INSTAGRAM -> R.string.app_instagram
    AppId.YOUTUBE -> R.string.app_youtube
    AppId.TIKTOK -> R.string.app_tiktok
    AppId.X -> R.string.app_x
    AppId.SNAPCHAT -> R.string.app_snapchat
    AppId.FACEBOOK -> R.string.app_facebook
}
