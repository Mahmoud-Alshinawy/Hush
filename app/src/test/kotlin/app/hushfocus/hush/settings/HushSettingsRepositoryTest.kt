package app.hushfocus.hush.settings

import androidx.datastore.preferences.core.edit
import app.hushfocus.hush.core.GlobalSettings
import app.hushfocus.hush.core.HomeFeedMode
import app.hushfocus.hush.core.InstagramSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HushSettingsRepositoryTest {
    private val folder = TemporaryFolder()
    private val stores = StoreRule(folder)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(folder).around(stores)

    private fun repository() = HushSettingsRepository(stores.open("settings"))

    /** Closes the current store and opens the file again, like a restart. */
    private fun reopened(): HushSettingsRepository {
        stores.closeAll()
        return repository()
    }

    @Test
    fun emptyStoreGivesSpecDefaults() = runBlocking {
        val repo = repository()
        assertEquals(GlobalSettings(hushEnabled = true), repo.globalSettings.first())
        assertEquals(InstagramSettings(), repo.instagramSettings.first())
        assertNull(repo.instagramAllowanceMinutes.first())
    }

    @Test
    fun everySetterRoundTrips() = runBlocking {
        val repo = repository()
        repo.setHushEnabled(false)
        repo.setAllowReelsSentInDms(false)
        repo.setHomeFeedMode(HomeFeedMode.HIDDEN)
        repo.setStopAtCaughtUp(false)
        repo.setHideExplore(true)
        repo.setHideStories(true)
        repo.setDmsOnly(true)
        repo.setInstagramAllowance(30)

        val again = reopened()
        assertEquals(GlobalSettings(hushEnabled = false), again.globalSettings.first())
        assertEquals(
            InstagramSettings(
                allowReelsSentInDms = false,
                homeFeedMode = HomeFeedMode.HIDDEN,
                stopAtCaughtUp = false,
                hideExplore = true,
                hideStories = true,
                dmsOnly = true,
            ),
            again.instagramSettings.first(),
        )
        assertEquals(30, again.instagramAllowanceMinutes.first())
    }

    @Test
    fun stopAtCaughtUpWhileHiddenIsStoredButNotShown() = runBlocking {
        val repo = repository()
        repo.setHomeFeedMode(HomeFeedMode.HIDDEN)
        repo.setStopAtCaughtUp(true)
        val settings = repo.instagramSettings.first()
        assertTrue(settings.stopAtCaughtUp)
        assertFalse(SettingsRules.isStopAtCaughtUpShown(settings))
    }

    @Test
    fun allowanceSwitchesBetweenOffAndMinutes() = runBlocking {
        val repo = repository()
        repo.setInstagramAllowance(15)
        assertEquals(15, repo.instagramAllowanceMinutes.first())
        repo.setInstagramAllowance(null)
        assertNull(repo.instagramAllowanceMinutes.first())
        repo.setInstagramAllowance(60)
        assertEquals(60, reopened().instagramAllowanceMinutes.first())
    }

    @Test
    fun allowanceOutsideTheListIsRefused() = runBlocking {
        val repo = repository()
        assertFailsWith<IllegalArgumentException> { repo.setInstagramAllowance(7) }
        assertNull(repo.instagramAllowanceMinutes.first())
    }

    @Test
    fun storedAllowanceOutsideTheListReadsAsOff() = runBlocking {
        val store = stores.open("settings")
        store.edit { it[SettingsKeys.IG_REELS_ALLOWANCE_MIN] = 7 }
        assertNull(HushSettingsRepository(store).instagramAllowanceMinutes.first())
    }

    @Test
    fun unknownHomeFeedModeReadsAsNormal() = runBlocking {
        val store = stores.open("settings")
        store.edit { it[SettingsKeys.IG_HOME_FEED_MODE] = "SIDEWAYS" }
        assertEquals(HomeFeedMode.NORMAL, HushSettingsRepository(store).instagramSettings.first().homeFeedMode)
    }

    @Test
    fun corruptFileGivesDefaults() = runBlocking {
        stores.file("settings").writeBytes(byteArrayOf(0x13, 0x37, 0x7F, 0x00, 0x42, 0x13))
        val repo = repository()
        assertEquals(GlobalSettings(), repo.globalSettings.first())
        assertEquals(InstagramSettings(), repo.instagramSettings.first())
        assertNull(repo.instagramAllowanceMinutes.first())

        // The store keeps working after the corrupt file was replaced.
        repo.setHideStories(true)
        assertTrue(repo.instagramSettings.first().hideStories)
    }
}
