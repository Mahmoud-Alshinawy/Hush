package app.hushfocus.hush.cover

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import app.hushfocus.hush.R
import app.hushfocus.hush.core.CoverKind
import java.util.EnumMap

/**
 * Cover windows on the real WindowManager: one TYPE_ACCESSIBILITY_OVERLAY window per kind.
 * Main thread only (CoverManager makes sure of that).
 *
 * Windows are placed in raw screen coordinates (status bar and cutout included), so a
 * CoverRect taken from getBoundsInScreen lines up exactly. They never take focus, so the
 * keyboard and Back keep working. A hidden cover is invisible AND untouchable, so it
 * can't swallow taps meant for the keyboard or dialog it is making way for.
 */
class WindowManagerCoverWindows(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTap: (CoverKind) -> Unit,
) : CoverWindows {
    private val views = EnumMap<CoverKind, View>(CoverKind::class.java)

    override fun add(kind: CoverKind, layout: CoverLayout, visible: Boolean) {
        if (views.containsKey(kind)) {
            update(kind, layout, visible)
            return
        }
        val view = createView(kind)
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        windowManager.addView(view, layoutParams(kind, layout, visible))
        views[kind] = view
    }

    override fun update(kind: CoverKind, layout: CoverLayout, visible: Boolean) {
        val view = views[kind] ?: return
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        try {
            windowManager.updateViewLayout(view, layoutParams(kind, layout, visible))
        } catch (_: IllegalArgumentException) {
            views.remove(kind) // The window is already gone.
        }
    }

    override fun remove(kind: CoverKind) {
        val view = views.remove(kind) ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // Already removed: nothing to do.
        }
    }

    private fun createView(kind: CoverKind): View = TextView(context).apply {
        setBackgroundColor(COVER_COLOR)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        maxLines = 2
        text = context.getString(R.string.cover_label)
        contentDescription = context.getString(R.string.cover_content_description)
        setOnClickListener { onTap(kind) }
    }

    private fun layoutParams(kind: CoverKind, layout: CoverLayout, visible: Boolean) =
        WindowManager.LayoutParams(
            layout.width,
            layout.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS or (if (visible) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = layout.x
            y = layout.y
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "HushCover:$kind"
        }

    private companion object {
        const val COVER_COLOR = 0xFF202124.toInt()
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
