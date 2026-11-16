package app.hushfocus.hush.settings

import app.hushfocus.hush.core.AppId
import app.hushfocus.hush.core.HomeFeedMode
import app.hushfocus.hush.core.InstagramSettings
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRulesTest {
    @Test
    fun caughtUpShownOnlyInNormalMode() {
        assertTrue(SettingsRules.isStopAtCaughtUpShown(InstagramSettings(homeFeedMode = HomeFeedMode.NORMAL)))
        assertFalse(SettingsRules.isStopAtCaughtUpShown(InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN)))
    }

    @Test
    fun homeStatusCombinations() {
        assertEquals(HomeStatus.HUSH_OFF, SettingsRules.homeStatus(hushEnabled = false, serviceEnabled = false))
        assertEquals(HomeStatus.HUSH_OFF, SettingsRules.homeStatus(hushEnabled = false, serviceEnabled = true))
        assertEquals(HomeStatus.SERVICE_OFF, SettingsRules.homeStatus(hushEnabled = true, serviceEnabled = false))
        assertEquals(HomeStatus.RUNNING, SettingsRules.homeStatus(hushEnabled = true, serviceEnabled = true))
    }

    @Test
    fun allowanceOptionsAreTheFixedList() {
        assertEquals(listOf(5, 10, 15, 20, 30, 45, 60), ReelsAllowance.OPTIONS_MINUTES)
        assertTrue(ReelsAllowance.isValid(null))
        assertFalse(ReelsAllowance.isValid(0))
    }

    @Test
    fun allowanceKeysParseBackAndOthersDont() {
        val date = LocalDate.of(2026, 1, 2)
        val key = SettingsKeys.allowance(AppId.YOUTUBE, date)
        assertEquals(AppId.YOUTUBE to date, SettingsKeys.parseAllowance(key.name))
        assertNull(SettingsKeys.parseAllowance(SettingsKeys.HUSH_ENABLED.name))
        assertNull(SettingsKeys.parseAllowance("v1.allowance.NOPE.2026-01-02"))
        assertNull(SettingsKeys.parseAllowance("v1.allowance.INSTAGRAM.yesterday"))
    }
}
