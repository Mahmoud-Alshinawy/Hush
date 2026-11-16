package app.hushfocus.hush.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hushfocus.hush.R
import app.hushfocus.hush.core.HomeFeedMode
import app.hushfocus.hush.settings.HushSettingsRepository
import app.hushfocus.hush.settings.ReelsAllowance
import app.hushfocus.hush.settings.SettingsRules
import kotlinx.coroutines.launch

/** Instagram's settings (spec §2.1, §2.8). Every change is saved immediately. */
@Composable
fun InstagramSettingsScreen(repository: HushSettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Null until the stored values are loaded, so defaults never flash before the real ones.
    val settings by repository.instagramSettings.collectAsState(initial = null)
    val allowance by repository.instagramAllowanceMinutes.collectAsState(initial = null)
    fun save(block: suspend HushSettingsRepository.() -> Unit) {
        scope.launch { repository.block() }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(stringResource(R.string.app_instagram), onBack)
            val current = settings ?: return@Column

            SwitchRow(
                title = stringResource(R.string.ig_allow_dm_reels),
                summary = stringResource(R.string.ig_allow_dm_reels_summary),
                checked = current.allowReelsSentInDms,
                onCheckedChange = { save { setAllowReelsSentInDms(it) } },
            )

            SectionTitle(stringResource(R.string.ig_home_feed))
            RadioRow(
                label = stringResource(R.string.ig_home_feed_normal),
                selected = current.homeFeedMode == HomeFeedMode.NORMAL,
                onSelect = { save { setHomeFeedMode(HomeFeedMode.NORMAL) } },
            )
            RadioRow(
                label = stringResource(R.string.ig_home_feed_hidden),
                selected = current.homeFeedMode == HomeFeedMode.HIDDEN,
                onSelect = { save { setHomeFeedMode(HomeFeedMode.HIDDEN) } },
            )
            if (SettingsRules.isStopAtCaughtUpShown(current)) {
                SwitchRow(
                    title = stringResource(R.string.ig_stop_at_caught_up),
                    checked = current.stopAtCaughtUp,
                    onCheckedChange = { save { setStopAtCaughtUp(it) } },
                )
            }

            SwitchRow(
                title = stringResource(R.string.ig_hide_explore),
                checked = current.hideExplore,
                onCheckedChange = { save { setHideExplore(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.ig_hide_stories),
                checked = current.hideStories,
                onCheckedChange = { save { setHideStories(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.ig_dms_only),
                checked = current.dmsOnly,
                onCheckedChange = { save { setDmsOnly(it) } },
            )

            SectionTitle(stringResource(R.string.ig_allowance))
            Text(
                stringResource(R.string.ig_allowance_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            RadioRow(
                label = stringResource(R.string.allowance_off),
                selected = allowance == null,
                onSelect = { save { setInstagramAllowance(null) } },
            )
            ReelsAllowance.OPTIONS_MINUTES.forEach { minutes ->
                RadioRow(
                    label = pluralStringResource(R.plurals.allowance_minutes, minutes, minutes),
                    selected = allowance == minutes,
                    onSelect = { save { setInstagramAllowance(minutes) } },
                )
            }
        }
    }
}
