package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.ScrollRecord

/**
 * Merges consecutive scroll events from the same source (same package, id, class and
 * bounds) that fall within [windowMs] of the first one into a single record: first
 * fromIndex, last toIndex and itemCount, summed deltas, and an event count.
 */
class ScrollMerger(private val windowMs: Long = 500) {
    private var pending: ScrollRecord? = null

    /** Adds a scroll; returns the previous merged record if this one could not join it. */
    fun add(record: ScrollRecord): ScrollRecord? {
        val current = pending
        if (current != null && canMerge(current, record)) {
            pending = current.copy(
                toIndex = record.toIndex,
                itemCount = record.itemCount,
                scrollDeltaX = current.scrollDeltaX + record.scrollDeltaX,
                scrollDeltaY = current.scrollDeltaY + record.scrollDeltaY,
                events = current.events + record.events,
            )
            return null
        }
        pending = record
        return current
    }

    /** Returns the pending merged record, if any, and clears it. */
    fun flush(): ScrollRecord? = pending.also { pending = null }

    /** When the pending record's window closes, or null if nothing is pending. */
    fun windowEndMs(): Long? = pending?.let { it.timeMs + windowMs }

    private fun canMerge(a: ScrollRecord, b: ScrollRecord): Boolean =
        a.packageName == b.packageName &&
            a.sourceId == b.sourceId &&
            a.sourceClass == b.sourceClass &&
            a.bounds == b.bounds &&
            b.timeMs - a.timeMs <= windowMs
}
