package app.hushfocus.hush.rules

import app.hushfocus.hush.core.ScrollObservation
import app.hushfocus.hush.core.SwipeSource
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.ScrollRecord

class InstagramScrollMapper {
    fun map(record: ScrollRecord, currentSnapshot: ScreenSnapshot?): ScrollObservation {
        val tree = currentSnapshot?.let(::InstagramTree)
        val pager = tree?.takeIf { it.reliable }?.let(::verifiedPager)
        val sourceBounds = record.bounds
        val ownPager = record.packageName == InstagramRules.PACKAGE_NAME && tree != null && pager != null &&
            record.sourceId == InstagramRules.REEL_PAGER && record.sourceClass == InstagramRules.REEL_PAGER_CLASS &&
            sourceBounds != null && boundsPositive(sourceBounds) && sourceBounds == tree.nodes[pager].bounds
        val height = sourceBounds?.let { boundsHeight(it).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() } ?: 0
        return ScrollObservation(record.timeMs, if (ownPager) SwipeSource.REEL_PAGER else SwipeSource.OTHER_CONTENT,
            record.scrollDeltaY, record.scrollDeltaX, height, record.fromIndex.takeIf { it >= 0 })
    }
}
