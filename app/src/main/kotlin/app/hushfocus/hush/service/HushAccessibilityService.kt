package app.hushfocus.hush.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import app.hushfocus.hush.cover.CoverManager
import app.hushfocus.hush.cover.debug.ServiceDebugHooks
import app.hushfocus.hush.inspector.Inspector
import app.hushfocus.hush.inspector.InspectorCapture
import app.hushfocus.hush.inspector.InspectorCounters
import app.hushfocus.hush.inspector.InspectorSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Hush's accessibility service — still OBSERVE-ONLY.
 *
 * It never presses Back and never taps. It owns the CoverManager, but no app rule asks
 * for a cover yet (only the debug-build demo does), so in a release build it draws
 * nothing. Blocking arrives in later tasks, once each app's rules have been written
 * from Inspector captures. With the Inspector off it reads no window trees at all
 * (see InspectorCounters); the CoverManager reads the window list only while a cover exists.
 */
class HushAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var inspectorOn = false
    private var capture: InspectorCapture? = null
    private var coverManager: CoverManager? = null

    override fun onServiceConnected() {
        // The system can reconnect the same service instance: drop what the last connection made.
        tearDown()
        val coverManager = CoverManager(this).also { coverManager = it }
        ServiceDebugHooks.onConnected(coverManager)

        val capture = InspectorCapture(this, Inspector.store(this)).also { capture = it }
        scope.launch {
            InspectorSettings(this@HushAccessibilityService).enabled.collect { on ->
                inspectorOn = on
                if (!on) capture.cancel()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Window-list changes have no package; they drive the cover hide rules.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) coverManager?.onWindowsChanged()

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        coverManager?.onConfigurationChanged(newConfig)
    }

    override fun onInterrupt() {
        coverManager?.hideAll()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        coverManager?.hideAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        scope.cancel()
        super.onDestroy()
    }

    /** Removes all covers and stops the Inspector capture and its settings collector. */
    private fun tearDown() {
        ServiceDebugHooks.onDisconnected()
        coverManager?.hideAll()
        coverManager = null
        scope.coroutineContext.cancelChildren()
        capture?.stop()
        capture = null
        inspectorOn = false
    }
}
