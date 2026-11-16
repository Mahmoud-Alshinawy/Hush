package app.hushfocus.hush.cover

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect

/**
 * Shows, moves, hides and removes Hush's cover windows (spec §3.1.5).
 *
 * Every call may come from any thread; WindowManager work always runs on the main thread.
 * The window list is read only while at least one cover exists, so with no covers this
 * reads nothing at all.
 */
class CoverManager(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val controller = CoverController(
        WindowManagerCoverWindows(service, windowManager) { kind -> onCoverTapped(kind) },
    )
    private var display = DisplayKey.of(service.resources.configuration)

    /** Called when the user taps a visible cover; Hush wires this up with the app rules. */
    @Volatile
    var onCoverTapped: (CoverKind) -> Unit = {}

    fun show(targetPackage: String, kind: CoverKind, rect: CoverRect) = onMain {
        controller.show(targetPackage, kind, rect, readScreen())
    }

    fun hide(kind: CoverKind) = onMain { controller.hide(kind) }

    fun hideAll() = onMain { controller.hideAll() }

    /** Removes the covers drawn over [targetPackage], e.g. when the user leaves that app. */
    fun removeAllFor(targetPackage: String) = onMain { controller.removeAllFor(targetPackage) }

    /** On TYPE_WINDOWS_CHANGED: re-checks the hide rules. */
    fun onWindowsChanged() = onMain {
        if (controller.hasCovers) controller.onWindowsChanged(readScreen())
    }

    /** A rotation or display-size change hides all covers until fresh rects arrive. */
    fun onConfigurationChanged(configuration: Configuration) = onMain {
        val next = DisplayKey.of(configuration)
        if (next != display) {
            display = next
            controller.onDisplayChanged()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.getMainLooper().isCurrentThread) block() else handler.post(block)
    }

    private fun readScreen(): ScreenState = ScreenState(
        display = windowManager.currentWindowMetrics.bounds.toScreenRect(),
        windows = service.windows.map(::snapshot),
    )

    private fun snapshot(window: AccessibilityWindowInfo) = WindowSnapshot(
        type = when (window.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> WindowType.APPLICATION
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowType.INPUT_METHOD
            AccessibilityWindowInfo.TYPE_SYSTEM -> WindowType.SYSTEM
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowType.ACCESSIBILITY_OVERLAY
            else -> WindowType.OTHER
        },
        layer = window.layer,
        bounds = Rect().also(window::getBoundsInScreen).toScreenRect(),
        active = window.isActive,
        focused = window.isFocused,
        ownerPackage = window.root?.packageName?.toString(),
    )

    private fun Rect.toScreenRect() = ScreenRect(left, top, right, bottom)

    private data class DisplayKey(val orientation: Int, val widthDp: Int, val heightDp: Int, val densityDpi: Int) {
        companion object {
            fun of(c: Configuration) = DisplayKey(c.orientation, c.screenWidthDp, c.screenHeightDp, c.densityDpi)
        }
    }
}
