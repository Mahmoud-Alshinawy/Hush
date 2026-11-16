package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstagramSnapshotAssemblerTest {
    private val time = FakeTime()
    private val store = InMemoryAllowanceStore()
    private val assembler = InstagramSnapshotAssembler(0.12, store)
    private val policy = GlobalPolicy()
    private val rect = CoverRect(0, 100, 400, 700)

    private fun player(reply: Boolean = true, index: Int = 0) = InstagramObservation(
        time.nowMs, InstagramScreen.REEL_PLAYER, replyBoxVisible = reply, pageIndex = index,
    )
    private fun snapshot(observation: InstagramObservation) = assembler.assemble(observation, time.zoneId)

    @Test fun dmReelWatchThenSwipeBackEndToEnd() {
        snapshot(InstagramObservation(time.nowMs, InstagramScreen.DM_CHAT))
        time.advanceBy(100)
        assertEquals(listOf(Action.Allow), policy.decide(snapshot(player())).actions)
        time.advanceBy(1_000)
        assertEquals(listOf(Action.Allow), policy.decide(snapshot(player(reply = false))).actions)
        time.advanceBy(10)
        val back = policy.decide(snapshot(player(reply = false, index = 1)))
        assertEquals(listOf(Action.Back), back.actions)
        assembler.recordDecision(back)
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        time.advanceBy(10)
        assertTrue(policy.decide(snapshot(InstagramObservation(time.nowMs, InstagramScreen.DM_CHAT))).actions.isEmpty())
    }

    @Test fun homeFeedReelBacksImmediately() {
        snapshot(InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED))
        time.advanceBy(100)
        val input = snapshot(player(reply = false))
        assertEquals(ReelOrigin.HOME_FEED, input.instagram.reel.origin)
        assertEquals(listOf(Action.Back), policy.decide(input).actions)
    }

    @Test fun rawCommentScrollIsIgnoredAndRawPagerScrollBacks() {
        snapshot(player())
        time.advanceBy(100)
        assembler.observeScroll(ScrollObservation(time.nowMs, SwipeSource.OTHER_CONTENT, 200, 0, 1_000))
        assertEquals(listOf(Action.Allow), policy.decide(snapshot(player())).actions)
        assembler.observeScroll(ScrollObservation(time.nowMs, SwipeSource.REEL_PAGER, 120, 0, 1_000))
        assertEquals(listOf(Action.Back), policy.decide(snapshot(player())).actions)
    }

    @Test fun openingReplyBoxWinsEvenWhenPreviousScreenWasHome() {
        snapshot(InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED))
        time.advanceBy(100)
        val input = snapshot(player())
        assertEquals(ReelOrigin.DM, input.instagram.reel.origin)
        assertEquals(listOf(Action.Allow), policy.decide(input).actions)
    }

    @Test fun lateReplyBox599And600MsProduceDifferentPolicyResults() {
        for ((delay, origin, action) in listOf(
            Triple(599L, ReelOrigin.DM, Action.Allow), Triple(600L, ReelOrigin.UNRESOLVED, Action.Back),
        )) {
            val current = InstagramSnapshotAssembler(0.12, InMemoryAllowanceStore())
            current.assemble(InstagramObservation(0, InstagramScreen.REEL_PLAYER), time.zoneId)
            val input = current.assemble(InstagramObservation(delay, InstagramScreen.REEL_PLAYER,
                replyBoxVisible = true), time.zoneId)
            assertEquals(origin, input.instagram.reel.origin)
            assertEquals(listOf(action), policy.decide(input).actions)
        }
    }

    @Test fun linkColdStartUsesFiveSecondsInsteadOfProfileTiming() {
        assembler.observeLink(LinkOpenedObservation(time.nowMs))
        time.advanceBy(4_999)
        val input = snapshot(player(reply = false).copy(usageAccessGranted = true))
        assertEquals(ReelOrigin.LINK, input.instagram.reel.origin)
        assertEquals(listOf(Action.Allow), policy.decide(input).actions)
    }

    @Test fun expiredLinkWaitsOnlyForOpeningReplyBoxThenBacks() {
        assembler.observeLink(LinkOpenedObservation(time.nowMs))
        time.advanceBy(5_001)
        assertEquals(ReelOrigin.UNRESOLVED, snapshot(player(reply = false)
            .copy(usageAccessGranted = true)).instagram.reel.origin)
        time.advanceBy(600)
        assertEquals(listOf(Action.Back), policy.decide(snapshot(player(reply = false))).actions)
    }

    @Test fun cooldownRecheckActsOnCurrentBlockedOrigin() {
        snapshot(InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED))
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        time.advanceBy(100)
        val skipped = policy.decide(snapshot(player(reply = false)))
        assembler.recordDecision(skipped)
        assertEquals(PolicyDecision(recheckAfterMs = 900), skipped)
        time.advanceBy(900)
        assertEquals(listOf(Action.Back), policy.decide(snapshot(player(reply = false))).actions)
    }

    @Test fun cooldownRecheckNeverReplaysBackAfterScreenChanges() {
        snapshot(InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED))
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        time.advanceBy(100)
        assembler.recordDecision(policy.decide(snapshot(player(reply = false))))
        time.advanceBy(900)
        assertEquals(PolicyDecision(), policy.decide(snapshot(InstagramObservation(time.nowMs, InstagramScreen.DM_CHAT))))
    }

    @Test fun policyDecisionAloneDoesNotStartCooldown() {
        val observation = InstagramObservation(time.nowMs, InstagramScreen.REELS_TAB,
            targets = setOf(ActionTarget.DMS_TAB))
        val decision = policy.decide(snapshot(observation))
        assembler.recordDecision(decision)
        time.advanceBy(1)
        val input = snapshot(observation.copy(atMs = time.nowMs))
        assertNull(input.msSinceLastNavigationAction)
        assertEquals(listOf(Action.Tap(ActionTarget.DMS_TAB)), policy.decide(input).actions)
    }

    @Test fun failedReelsTabRedirectStillFallsBackAfterCooldown() {
        val observation = InstagramObservation(time.nowMs, InstagramScreen.REELS_TAB,
            targets = setOf(ActionTarget.DMS_TAB))
        val tap = policy.decide(snapshot(observation)).actions.single()
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, tap, false))
        time.advanceBy(100)
        val duringCooldown = snapshot(observation.copy(atMs = time.nowMs))
        assertEquals(ActionTarget.DMS_TAB, duringCooldown.instagram.failedTap)
        val skipped = policy.decide(duringCooldown)
        assertEquals(PolicyDecision(recheckAfterMs = 900), skipped)
        assembler.recordDecision(skipped)
        time.advanceBy(900)
        val rechecked = snapshot(observation.copy(atMs = time.nowMs))
        assertNull(rechecked.instagram.failedTap)
        assertEquals(listOf(Action.Back), policy.decide(rechecked).actions)
    }

    @Test fun backDoesNotClearFailedRedirectWhileReelsTabRemainsSelected() {
        val observation = failedRedirect()
        time.advanceBy(1_000)
        val firstBack = policy.decide(snapshot(observation.copy(atMs = time.nowMs)))
        assertEquals(listOf(Action.Back), firstBack.actions)
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        time.advanceBy(1_000)
        val stillOnTab = snapshot(observation.copy(atMs = time.nowMs))
        assertTrue(stillOnTab.instagram.reelsTabRedirectFailed)
        assertNull(stillOnTab.instagram.failedTap)
        assertEquals(listOf(Action.Back), policy.decide(stillOnTab).actions)
    }

    @Test fun failedRedirectPersistsIntoPlayerAndThroughItsSheet() {
        failedRedirect()
        time.advanceBy(100)
        val sheet = snapshot(player(reply = false).copy(sheetOrMenuOpen = true))
        assertEquals(ReelOrigin.REELS_TAB, sheet.instagram.reel.origin)
        assertTrue(sheet.instagram.reelsTabRedirectFailed)
        assertTrue(policy.decide(sheet).actions.isEmpty())
        time.advanceBy(900)
        val closed = snapshot(player(reply = false))
        assertTrue(closed.instagram.reelsTabRedirectFailed)
        assertEquals(listOf(Action.Back), policy.decide(closed).actions)
    }

    @Test fun selectedTabFlagKeepsRedirectFailureAcrossOverlayUi() {
        val observation = failedRedirect()
        time.advanceBy(100)
        val overlay = snapshot(InstagramObservation(time.nowMs, InstagramScreen.OVERLAY_UI,
            reelsTabSelected = true, sheetOrMenuOpen = true))
        assertTrue(overlay.instagram.reelsTabRedirectFailed)
        time.advanceBy(900)
        assertEquals(listOf(Action.Back), policy.decide(snapshot(observation.copy(atMs = time.nowMs))).actions)
    }

    @Test fun inboxUnknownAndContextExitClearFailureBeforeNewTabEntry() {
        for (screen in listOf(InstagramScreen.DM_INBOX, InstagramScreen.DM_CHAT,
            InstagramScreen.HOME_FEED, InstagramScreen.PROFILE, InstagramScreen.UNKNOWN)) {
            val current = InstagramSnapshotAssembler(0.12, InMemoryAllowanceStore())
            val tab = InstagramObservation(0, InstagramScreen.REELS_TAB, reelsTabSelected = true,
                targets = setOf(ActionTarget.DMS_TAB))
            val tap = policy.decide(current.assemble(tab, time.zoneId)).actions.single()
            current.recordExecution(ActionExecutionFeedback(0, tap, false))
            val exited = current.assemble(InstagramObservation(100, screen,
                reelsTabSelected = screen == InstagramScreen.UNKNOWN || screen == InstagramScreen.DM_INBOX), time.zoneId)
            assertFalse(exited.instagram.reelsTabRedirectFailed, screen.toString())
            val returned = current.assemble(tab.copy(atMs = 1_000), time.zoneId)
            assertFalse(returned.instagram.reelsTabRedirectFailed)
            assertNull(returned.instagram.failedTap)
            assertEquals(listOf(Action.Tap(ActionTarget.DMS_TAB)), policy.decide(returned).actions)
        }
    }

    @Test fun failedDmTapOutsideReelsContextDoesNotLatchRedirectFailure() {
        val home = InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED,
            tabBarVisible = true, targets = setOf(ActionTarget.DMS_TAB))
        val input = assembler.assemble(home, time.zoneId, settings = InstagramSettings(dmsOnly = true))
        val tap = policy.decide(input).actions.single()
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, tap, false))
        time.advanceBy(100)
        val next = snapshot(home.copy(atMs = time.nowMs))
        assertEquals(ActionTarget.DMS_TAB, next.instagram.failedTap)
        assertFalse(next.instagram.reelsTabRedirectFailed)
    }

    @Test fun explicitAppExitClearsRedirectFailure() {
        val tab = failedRedirect()
        assembler.leaveInstagram(time.nowMs, time.zoneId)
        time.advanceBy(1_000)
        assertFalse(snapshot(tab.copy(atMs = time.nowMs)).instagram.reelsTabRedirectFailed)
    }

    private fun failedRedirect(): InstagramObservation {
        val observation = InstagramObservation(time.nowMs, InstagramScreen.REELS_TAB,
            reelsTabSelected = true, targets = setOf(ActionTarget.DMS_TAB))
        val tap = policy.decide(snapshot(observation)).actions.single()
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, tap, false))
        return observation
    }

    @Test fun cooldownSuppressedPanelTapIsNotRecordedAsAttempted() {
        val settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN)
        val covers = ExecutedCoverState(mapOf(CoverKind.FEED to rect), audioFocusHeld = true)
        fun input(interaction: InstagramInteraction? = null) = assembler.assemble(
            InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED,
                targets = setOf(ActionTarget.INSTAGRAM_LOGO), coverBounds = mapOf(CoverKind.FEED to rect)),
            time.zoneId, settings, executedCovers = covers, interaction = interaction,
        )
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        val skipped = policy.decide(input(InstagramInteraction.OPEN_FAVORITES))
        assertEquals(PolicyDecision(recheckAfterMs = 1_000), skipped)
        assembler.recordDecision(skipped)
        time.advanceBy(500)
        assertTrue(input().instagram.feedNavigation!!.attemptedTargets.isEmpty())
        time.advanceBy(500)
        val decision = policy.decide(input())
        assertEquals(listOf(Action.Tap(ActionTarget.INSTAGRAM_LOGO)), decision.actions)
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, decision.actions.single(), true))
        assertEquals(setOf(ActionTarget.INSTAGRAM_LOGO), input().instagram.feedNavigation!!.attemptedTargets)
    }

    @Test fun failedPanelExecutionFeedsFailureOnceAndStopsWorkflow() {
        val home = InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED,
            targets = setOf(ActionTarget.INSTAGRAM_LOGO))
        assembler.assemble(home, time.zoneId, interaction = InstagramInteraction.OPEN_FAVORITES)
        assembler.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Tap(ActionTarget.INSTAGRAM_LOGO), false))
        time.advanceBy(1)
        val input = snapshot(home.copy(atMs = time.nowMs))
        assertEquals(ActionTarget.INSTAGRAM_LOGO, input.instagram.failedTap)
        val failure = policy.decide(input)
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)), failure.actions)
        assembler.recordDecision(failure)
        val stopped = snapshot(home.copy(atMs = time.nowMs))
        assertNull(stopped.instagram.failedTap)
        assertEquals(FeedNavigationStep.STOPPED, stopped.instagram.feedNavigation!!.step)
        assertEquals(PolicyDecision(), policy.decide(stopped))
    }

    @Test fun everyFreeReelOriginConsumesAllowanceWhileVisible() {
        for ((screen, origin) in listOf(InstagramScreen.DM_CHAT to ReelOrigin.DM,
            InstagramScreen.PROFILE to ReelOrigin.PROFILE, InstagramScreen.STORY_VIEWER to ReelOrigin.STORY,
            InstagramScreen.UNKNOWN to ReelOrigin.LINK)) {
            val current = InstagramSnapshotAssembler(0.12, InMemoryAllowanceStore())
            current.assemble(InstagramObservation(0, screen), time.zoneId)
            if (origin == ReelOrigin.LINK) current.observeLink(LinkOpenedObservation(1))
            val opened = InstagramObservation(100, InstagramScreen.REEL_PLAYER,
                replyBoxVisible = origin == ReelOrigin.DM, usageAccessGranted = true)
            val first = current.assemble(opened, time.zoneId, allowanceLimitMs = 2_000)
            assertEquals(origin, first.instagram.reel.origin)
            val later = current.assemble(opened.copy(atMs = 1_100), time.zoneId, allowanceLimitMs = 2_000)
            assertEquals(1_000L, later.instagram.allowance.remainingMs)
        }
    }

    @Test fun allowanceExhaustionMessageIsDeliveredOnceWithImmediateBlocking() {
        snapshot(InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED))
        val first = player(reply = false)
        assembler.assemble(first, time.zoneId, allowanceLimitMs = 1_000)
        time.advanceBy(1_000)
        val depleted = assembler.assemble(first.copy(atMs = time.nowMs), time.zoneId, allowanceLimitMs = 1_000)
        assertEquals(listOf(Action.Back, Action.ShowMessage(HushMessage.ALLOWANCE_USED_UP)), policy.decide(depleted).actions)
        val again = assembler.assemble(first.copy(atMs = time.nowMs), time.zoneId, allowanceLimitMs = 1_000)
        assertFalse(again.instagram.allowance.justExhausted)
    }

    @Test fun sheetKeepsSessionWhileRemovingExistingReelsCover() {
        snapshot(player())
        time.advanceBy(2_000)
        val input = assembler.assemble(player(reply = false).copy(sheetOrMenuOpen = true), time.zoneId,
            executedCovers = ExecutedCoverState(mapOf(CoverKind.REELS_TAB to rect)))
        assertEquals(listOf(Action.HideCover(CoverKind.REELS_TAB)), policy.decide(input).actions)
        time.advanceBy(10)
        assertEquals(listOf(Action.Allow), policy.decide(snapshot(player(reply = false))).actions)
    }

    @Test fun unknownClearsExecutorOwnedCoversAndAudioWithoutNavigation() {
        val input = assembler.assemble(InstagramObservation(time.nowMs, InstagramScreen.UNKNOWN), time.zoneId,
            executedCovers = ExecutedCoverState(mapOf(CoverKind.REELS_TAB to rect, CoverKind.FEED to rect), true))
        assertEquals(listOf(Action.HideCover(CoverKind.REELS_TAB), Action.HideCover(CoverKind.FEED),
            Action.ReleaseAudioFocus), policy.decide(input).actions)
    }

    @Test fun leavingInstagramBreaksAllowanceContinuity() {
        assembler.assemble(player(), time.zoneId, allowanceLimitMs = 2_000)
        time.advanceBy(100)
        assembler.leaveInstagram(time.nowMs, time.zoneId)
        time.advanceBy(500)
        assertEquals(2_000L, assembler.assemble(player(), time.zoneId,
            allowanceLimitMs = 2_000).instagram.allowance.remainingMs)
    }
}
