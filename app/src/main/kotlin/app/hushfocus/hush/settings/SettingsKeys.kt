package app.hushfocus.hush.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.hushfocus.hush.core.AppId
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Every stored key, in one place. The "v1." prefix is the storage version: a change of
 * meaning gets a new prefix instead of reusing a key. A missing key means the spec default.
 */
object SettingsKeys {
    private const val V = "v1"

    val HUSH_ENABLED = booleanPreferencesKey("$V.global.hush_enabled")

    val IG_ALLOW_DM_REELS = booleanPreferencesKey("$V.instagram.allow_dm_reels")
    val IG_HOME_FEED_MODE = stringPreferencesKey("$V.instagram.home_feed_mode")
    val IG_STOP_AT_CAUGHT_UP = booleanPreferencesKey("$V.instagram.stop_at_caught_up")
    val IG_HIDE_EXPLORE = booleanPreferencesKey("$V.instagram.hide_explore")
    val IG_HIDE_STORIES = booleanPreferencesKey("$V.instagram.hide_stories")
    val IG_DMS_ONLY = booleanPreferencesKey("$V.instagram.dms_only")
    val IG_REELS_ALLOWANCE_MIN = intPreferencesKey("$V.instagram.reels_allowance_min")

    private const val ALLOWANCE_PREFIX = "$V.allowance."

    /** Milliseconds of allowance used by [app] on the local [date]. */
    fun allowance(app: AppId, date: LocalDate): Preferences.Key<Long> =
        longPreferencesKey("$ALLOWANCE_PREFIX${app.name}.$date")

    /** The app and date of an allowance key, or null for any other key. */
    fun parseAllowance(name: String): Pair<AppId, LocalDate>? {
        if (!name.startsWith(ALLOWANCE_PREFIX)) return null
        val parts = name.removePrefix(ALLOWANCE_PREFIX).split('.')
        if (parts.size != 2) return null
        val app = AppId.entries.firstOrNull { it.name == parts[0] } ?: return null
        val date = try {
            LocalDate.parse(parts[1])
        } catch (_: DateTimeParseException) {
            return null
        }
        return app to date
    }
}
