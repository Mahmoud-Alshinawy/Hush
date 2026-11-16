package app.hushfocus.hush.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import app.hushfocus.hush.core.GlobalSettings
import app.hushfocus.hush.core.HomeFeedMode
import app.hushfocus.hush.core.InstagramSettings
import app.hushfocus.hush.settings.SettingsKeys.HUSH_ENABLED
import app.hushfocus.hush.settings.SettingsKeys.IG_ALLOW_DM_REELS
import app.hushfocus.hush.settings.SettingsKeys.IG_DMS_ONLY
import app.hushfocus.hush.settings.SettingsKeys.IG_HIDE_EXPLORE
import app.hushfocus.hush.settings.SettingsKeys.IG_HIDE_STORIES
import app.hushfocus.hush.settings.SettingsKeys.IG_HOME_FEED_MODE
import app.hushfocus.hush.settings.SettingsKeys.IG_REELS_ALLOWANCE_MIN
import app.hushfocus.hush.settings.SettingsKeys.IG_STOP_AT_CAUGHT_UP
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Hush's saved settings. A missing or unreadable value is the spec default, which the
 * `:core` settings classes define; nothing here crashes on bad data.
 */
class HushSettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val globalSettings: Flow<GlobalSettings> = preferences.map { p ->
        val defaults = GlobalSettings()
        GlobalSettings(hushEnabled = p[HUSH_ENABLED] ?: defaults.hushEnabled)
    }

    val instagramSettings: Flow<InstagramSettings> = preferences.map { p ->
        val defaults = InstagramSettings()
        InstagramSettings(
            allowReelsSentInDms = p[IG_ALLOW_DM_REELS] ?: defaults.allowReelsSentInDms,
            homeFeedMode = p[IG_HOME_FEED_MODE]
                ?.let { name -> HomeFeedMode.entries.firstOrNull { it.name == name } }
                ?: defaults.homeFeedMode,
            stopAtCaughtUp = p[IG_STOP_AT_CAUGHT_UP] ?: defaults.stopAtCaughtUp,
            hideExplore = p[IG_HIDE_EXPLORE] ?: defaults.hideExplore,
            hideStories = p[IG_HIDE_STORIES] ?: defaults.hideStories,
            dmsOnly = p[IG_DMS_ONLY] ?: defaults.dmsOnly,
        )
    }

    /** Instagram's daily reels allowance in minutes; null means off (the default). */
    val instagramAllowanceMinutes: Flow<Int?> = preferences.map { ReelsAllowance.fromStored(it[IG_REELS_ALLOWANCE_MIN]) }

    suspend fun setHushEnabled(enabled: Boolean) = set(HUSH_ENABLED, enabled)

    suspend fun setAllowReelsSentInDms(allow: Boolean) = set(IG_ALLOW_DM_REELS, allow)

    suspend fun setHomeFeedMode(mode: HomeFeedMode) = set(IG_HOME_FEED_MODE, mode.name)

    suspend fun setStopAtCaughtUp(stop: Boolean) = set(IG_STOP_AT_CAUGHT_UP, stop)

    suspend fun setHideExplore(hide: Boolean) = set(IG_HIDE_EXPLORE, hide)

    suspend fun setHideStories(hide: Boolean) = set(IG_HIDE_STORIES, hide)

    suspend fun setDmsOnly(dmsOnly: Boolean) = set(IG_DMS_ONLY, dmsOnly)

    /** [minutes] must be null (off) or one of [ReelsAllowance.OPTIONS_MINUTES]. */
    suspend fun setInstagramAllowance(minutes: Int?) {
        require(ReelsAllowance.isValid(minutes)) { "Not an allowance option: $minutes" }
        dataStore.edit { p ->
            if (minutes == null) p.remove(IG_REELS_ALLOWANCE_MIN) else p[IG_REELS_ALLOWANCE_MIN] = minutes
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
