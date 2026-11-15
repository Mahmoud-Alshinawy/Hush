package app.hushfocus.hush.inspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrubberTest {
    private val instagram = LabelAllowList.labelsFor("com.instagram.android")
    private val youtube = LabelAllowList.labelsFor("com.google.android.youtube")

    @Test
    fun usernameBecomesPlaceholder() {
        assertEquals("‹text:11›", Scrubber.scrub("jane.doe_92", instagram))
    }

    @Test
    fun captionWithEmojiCountsCodePoints() {
        // 13 letters/spaces + 1 emoji (2 UTF-16 chars, 1 code point).
        assertEquals("‹text:14›", Scrubber.scrub("Sunset 🌅 vibes", instagram))
    }

    @Test
    fun messageBecomesPlaceholder() {
        assertEquals("‹text:24›", Scrubber.scrub("see you at 8 by the cafe", instagram))
    }

    @Test
    fun allowListedLabelIsKept() {
        assertEquals("Reels", Scrubber.scrub("Reels", instagram))
    }

    @Test
    fun labelOfAnotherAppIsScrubbed() {
        assertEquals("‹text:5›", Scrubber.scrub("Reels", youtube))
    }

    @Test
    fun matchIsCaseSensitive() {
        assertEquals("‹text:5›", Scrubber.scrub("reels", instagram))
    }

    @Test
    fun blankAndNullStayAsTheyAre() {
        assertEquals("", Scrubber.scrub("", instagram))
        assertEquals("  ", Scrubber.scrub("  ", instagram))
        assertNull(Scrubber.scrub(null, instagram))
    }

    @Test
    fun labelWithSelectedStateIsKept() {
        assertEquals("Reels, selected", Scrubber.scrub("Reels, selected", instagram))
    }

    @Test
    fun labelWithTabPositionIsKept() {
        assertEquals("Home, tab 1 of 5", Scrubber.scrub("Home, tab 1 of 5", instagram))
    }

    @Test
    fun severalStatesIgnoringCaseAreKept() {
        assertEquals("Home, Not selected, Tab 1 of 5", Scrubber.scrub("Home, Not selected, Tab 1 of 5", instagram))
    }

    @Test
    fun usernameAfterLabelIsScrubbed() {
        assertEquals("Profile, ‹text:8›", Scrubber.scrub("Profile, jane.doe", instagram))
    }

    @Test
    fun stateMixedWithOtherTextScrubsTheWholeRest() {
        assertEquals("Profile, ‹text:18›", Scrubber.scrub("Profile, selected, jane.doe", instagram))
    }

    @Test
    fun unknownLabelBeforeCommaScrubsEverything() {
        assertEquals("‹text:15›", Scrubber.scrub("jane, selected!", instagram))
    }
}
