package app.hushfocus.hush.inspector

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.hushfocus.hush.inspector.model.InspectorEntry
import app.hushfocus.hush.inspector.model.ScrollRecord

/**
 * Turns accessibility events into Inspector log entries, on its own background thread.
 *
 * Screen changes are debounced (400 ms trailing, 1 s max wait), read with NodeTreeReader
 * and skipped when the structure matches the previous snapshot of the same app.
 * Scroll events are merged by ScrollMerger and written when their 500 ms window closes.
 * Only the service calls this, and only while the Inspector is on.
 */
class InspectorCapture(
    private val service: AccessibilityService,
    private val store: InspectorStore,
) {
    private val thread = HandlerThread("hush-inspector").apply { start() }
    private val handler = Handler(thread.looper)

    // Touched only on the handler thread.
    private val debouncer = Debouncer()
    private val deduper = SnapshotDeduper()
    private val scrollMerger = ScrollMerger()
    private val reader = NodeTreeReader()
    private var pendingTrigger: String? = null

    private val captureRunnable = Runnable { captureScreen() }
    private val flushScrollsRunnable = Runnable { flushScrolls() }

    /** A window-state or content-changed event from a supported app. */
    fun onScreenChanged(eventType: Int) {
        val now = SystemClock.uptimeMillis()
        val trigger = AccessibilityEvent.eventTypeToString(eventType)
        handler.post {
            pendingTrigger = trigger
            handler.removeCallbacks(captureRunnable)
            handler.postAtTime(captureRunnable, debouncer.onEvent(now))
        }
    }

    /** A view-scrolled event from a supported app. Reads only the source's id, class and bounds. */
    fun onScrolled(event: AccessibilityEvent, packageName: String) {
        InspectorCounters.treeRead()
        val source = event.source
        val record = ScrollRecord(
            timeMs = System.currentTimeMillis(),
            packageName = packageName,
            sourceId = source?.viewIdResourceName,
            sourceClass = (source?.className ?: event.className)?.toString(),
            bounds = source?.let { Rect().also(it::getBoundsInScreen).toBounds() },
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
            scrollDeltaX = event.scrollDeltaX,
            scrollDeltaY = event.scrollDeltaY,
        )
        handler.post {
            scrollMerger.add(record)?.let(::save)
            handler.removeCallbacks(flushScrollsRunnable)
            scrollMerger.windowEndMs()?.let { end ->
                handler.postDelayed(flushScrollsRunnable, (end - System.currentTimeMillis()).coerceAtLeast(0))
            }
        }
    }

    /** The Inspector was switched off: drop the pending capture, keep merged scrolls. */
    fun cancel() {
        handler.post {
            handler.removeCallbacks(captureRunnable)
            pendingTrigger = null
            debouncer.onFired()
            flushScrolls()
        }
    }

    fun stop() {
        cancel()
        thread.quitSafely()
    }

    private fun captureScreen() {
        debouncer.onFired()
        val trigger = pendingTrigger ?: return
        pendingTrigger = null
        val snapshot = reader.read(service, trigger, System.currentTimeMillis()) ?: return
        if (deduper.isNew(snapshot)) save(snapshot)
    }

    private fun flushScrolls() {
        handler.removeCallbacks(flushScrollsRunnable)
        scrollMerger.flush()?.let(::save)
    }

    private fun save(entry: InspectorEntry) {
        store.append(entry)
        InspectorCounters.entrySaved()
        Log.d(TAG, "saved ${entry::class.simpleName} for ${entry.packageName}")
    }

    private companion object {
        const val TAG = "HushInspector"
    }
}
