package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverKind

/** The window operations CoverController needs; real ones use WindowManager, tests use a fake. */
interface CoverWindows {
    fun add(kind: CoverKind, layout: CoverLayout, visible: Boolean)

    fun update(kind: CoverKind, layout: CoverLayout, visible: Boolean)

    /** Must be safe to call for a cover that is already gone. */
    fun remove(kind: CoverKind)
}
