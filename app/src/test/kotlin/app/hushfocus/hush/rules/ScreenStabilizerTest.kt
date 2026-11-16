package app.hushfocus.hush.rules

import app.hushfocus.hush.core.InstagramScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenStabilizerTest {
    private val stabilizer = ScreenStabilizer()

    @Test fun unknownAt349MsKeepsPreviousClassificationButAt351MsDoesNot() {
        stabilizer.update(InstagramScreen.HOME_FEED, 0)
        stabilizer.update(InstagramScreen.UNKNOWN, 10)
        val held = stabilizer.update(InstagramScreen.UNKNOWN, 359)
        assertEquals(InstagramScreen.UNKNOWN, held.rawScreen)
        assertEquals(InstagramScreen.HOME_FEED, held.stableScreen)
        assertTrue(held.isHeld)
        val expired = stabilizer.update(InstagramScreen.UNKNOWN, 361)
        assertEquals(InstagramScreen.UNKNOWN, expired.stableScreen)
        assertFalse(expired.isHeld)
    }

    @Test fun unknownFloodCannotExtendTheFixedDeadline() {
        stabilizer.update(InstagramScreen.REEL_PLAYER, 0)
        for (at in listOf(10L, 100L, 200L, 300L, 359L)) {
            assertTrue(stabilizer.update(InstagramScreen.UNKNOWN, at).isHeld)
        }
        assertFalse(stabilizer.update(InstagramScreen.UNKNOWN, 360).isHeld)
    }

    @Test fun newRecognisedScreenResetsUnknownSequence() {
        stabilizer.update(InstagramScreen.HOME_FEED, 0)
        stabilizer.update(InstagramScreen.UNKNOWN, 10)
        assertEquals(InstagramScreen.REEL_PLAYER, stabilizer.update(InstagramScreen.REEL_PLAYER, 200).stableScreen)
        assertEquals(InstagramScreen.REEL_PLAYER, stabilizer.update(InstagramScreen.UNKNOWN, 500).stableScreen)
        assertEquals(InstagramScreen.UNKNOWN, stabilizer.update(InstagramScreen.UNKNOWN, 850).stableScreen)
    }

    @Test fun initialUnknownAndExplicitContextResetHaveNoHeldScreen() {
        assertFalse(stabilizer.update(InstagramScreen.UNKNOWN, 0).isHeld)
        stabilizer.update(InstagramScreen.REELS_TAB, 100)
        stabilizer.reset()
        assertEquals(InstagramScreen.UNKNOWN, stabilizer.update(InstagramScreen.UNKNOWN, 101).stableScreen)
    }
}
