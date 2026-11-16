package app.hushfocus.hush.rules

import app.hushfocus.hush.core.Action
import app.hushfocus.hush.core.InstagramPolicy
import app.hushfocus.hush.core.InstagramScreen
import app.hushfocus.hush.core.InstagramSnapshot
import app.hushfocus.hush.core.ReelSessionTracker
import app.hushfocus.hush.core.SwipeSource
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.ScrollRecord
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstagramScrollMapperTest {
    private val mapper = InstagramScrollMapper()
    private val player = InstagramFixtures.load("reel_from_dm_reply_box.json")
    private fun pagerRecord(snapshot: ScreenSnapshot = player): ScrollRecord {
        val tree = InstagramTree(snapshot)
        val pager = checkNotNull(verifiedPager(tree))
        return ScrollRecord(snapshot.timeMs, InstagramRules.PACKAGE_NAME, InstagramRules.REEL_PAGER,
            InstagramRules.REEL_PAGER_CLASS, tree.nodes[pager].bounds, 0, 0, 3, 0, 0)
    }

    @Test fun exactPagerSourceMapsToPagerAndUsesItsOwnHeight() {
        val record = pagerRecord().copy(fromIndex = 1, scrollDeltaY = 2004, scrollDeltaX = 3)
        val mapped = mapper.map(record, player)
        assertEquals(SwipeSource.REEL_PAGER, mapped.source)
        assertEquals(2004, mapped.sourceHeightPx)
        assertEquals(2004, mapped.deltaY)
        assertEquals(3, mapped.deltaX)
        assertEquals(1, mapped.fromIndex)
        assertEquals(record.timeMs, mapped.atMs)
    }

    @Test fun commentsShareAndMoreListsAreOtherContent() {
        for ((name, source) in listOf("comments_over_reel.json" to InstagramRules.HOME_VIEWPORT,
            "share_over_reel.json" to InstagramRules.SHARE_SHEET,
            "more_over_reel.json" to InstagramRules.EXPLORE_GRID)) {
            val snapshot = InstagramFixtures.load(name)
            val sourceNode = checkNotNull(InstagramTree(snapshot).unique(source))
            val node = snapshot.nodes[sourceNode]
            val record = ScrollRecord(snapshot.timeMs, snapshot.packageName, source, node.className,
                node.bounds, 0, 1, 10, 0, 200)
            assertEquals(SwipeSource.OTHER_CONTENT, mapper.map(record, snapshot).source, name)
        }
    }

    @Test fun matchingClassWithoutPagerIdIsNotEnough() {
        assertEquals(SwipeSource.OTHER_CONTENT, mapper.map(pagerRecord().copy(sourceId = "synthetic:carousel"), player).source)
    }

    @Test fun matchingIdWithoutVerifiedClassBoundsOrContextIsNotEnough() {
        val record = pagerRecord()
        for (altered in listOf(record.copy(sourceClass = "synthetic:other-class"),
            record.copy(bounds = Bounds(0, 0, 10, 10)), record.copy(bounds = null),
            record.copy(packageName = "synthetic.other"))) {
            assertEquals(SwipeSource.OTHER_CONTENT, mapper.map(altered, player).source)
        }
        assertEquals(SwipeSource.OTHER_CONTENT, mapper.map(record, null).source)
        assertEquals(SwipeSource.OTHER_CONTENT, mapper.map(record,
            InstagramFixtures.load("home_with_hidden_profile_nodes.json")).source)
    }

    @Test fun invalidHeightAndUnknownIndexAreRepresentedWithoutGuessing() {
        val mapped = mapper.map(pagerRecord().copy(bounds = Bounds(0, 200, 100, 100), fromIndex = -1), player)
        assertEquals(SwipeSource.OTHER_CONTENT, mapped.source)
        assertEquals(0, mapped.sourceHeightPx)
        assertEquals(null, mapped.fromIndex)
    }

    @Test fun commentScrollNeverBecomesSwipeButVerifiedPagerScrollDoes() {
        val detector = InstagramScreenDetector()
        val tracker = ReelSessionTracker(InstagramRules.REEL_SWIPE_FRACTION)
        tracker.observe(detector.detect(player))
        val comment = pagerRecord().copy(sourceId = InstagramRules.HOME_VIEWPORT, scrollDeltaY = 500)
        tracker.observeScroll(mapper.map(comment, player))
        assertTrue(tracker.snapshot(player.timeMs).changes.isEmpty())
        val record = pagerRecord().copy(scrollDeltaY = ceil(2004 * InstagramRules.REEL_SWIPE_FRACTION).toInt())
        tracker.observeScroll(mapper.map(record, player))
        assertEquals(listOf(Action.Back), InstagramPolicy().decide(InstagramSnapshot(
            InstagramScreen.REEL_PLAYER, reel = tracker.snapshot(player.timeMs), replyBoxVisible = true)).actions)
    }
}
