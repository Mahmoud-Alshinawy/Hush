package app.hushfocus.hush.settings

import app.hushfocus.hush.core.HomeFeedMode
import app.hushfocus.hush.core.InstagramSettings

/** Small UI rules, kept pure so they can be unit-tested. */
object SettingsRules {
    /** "Stop at 'You're all caught up'" only applies, and is only shown, in Normal mode (spec §2.1.2). */
    fun isStopAtCaughtUpShown(settings: InstagramSettings): Boolean =
        settings.homeFeedMode == HomeFeedMode.NORMAL

    fun homeStatus(hushEnabled: Boolean, serviceEnabled: Boolean): HomeStatus = when {
        !hushEnabled -> HomeStatus.HUSH_OFF
        !serviceEnabled -> HomeStatus.SERVICE_OFF
        else -> HomeStatus.RUNNING
    }
}

/**
 * What the Home screen reports. SERVICE_OFF covers both "service switched off" and
 * "restricted settings not allowed yet": Android doesn't let an app tell them apart.
 */
enum class HomeStatus { HUSH_OFF, SERVICE_OFF, RUNNING }
