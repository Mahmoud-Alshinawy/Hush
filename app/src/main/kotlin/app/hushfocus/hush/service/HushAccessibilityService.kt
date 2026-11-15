package app.hushfocus.hush.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import app.hushfocus.hush.inspector.Inspector
import app.hushfocus.hush.inspector.InspectorCapture
import app.hushfocus.hush.inspector.InspectorCounters
import app.hushfocus.hush.inspector.InspectorSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hush's accessibility service — currently an OBSERVE-ONLY shell.
 *
 * It never presses Back, never taps, never draws covers: blocking arrives in later tasks,
 * once each app's rules have been written from Inspector captures. For now it only
 * feeds the Inspector, and only for the supported apps while the Inspector is on.
 * With the Inspector off it reads no window trees at all (see InspectorCounters).
 */
class HushAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var inspectorOn = false
    private var capture: InspectorCapture? = null

    override fun onServiceConnected() {
        val capture = InspectorCapture(this, Inspector.store(this)).also { capture = it }
        scope.launch {
            InspectorSettings(this@HushAccessibilityService).enabled.collect { on ->
                inspectorOn = on
                if (!on) capture.cancel()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in SupportedApps.packages) return
        // Counted only for supported apps: counting Hush's own UI events would feed back
        // into the counter on screen and loop.
        InspectorCounters.supportedEventSeen()
        if (!inspectorOn) return
        val capture = capture ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> capture.onScreenChanged(event.eventType)

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> capture.onScrolled(event, packageName)

            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        capture?.stop()
        capture = null
        super.onDestroy()
    }
}
