package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverRect

/** A cover window's position and size in screen pixels, as WindowManager wants them. */
data class CoverLayout(val x: Int, val y: Int, val width: Int, val height: Int)

fun CoverRect.toLayout() = CoverLayout(x = left, y = top, width = right - left, height = bottom - top)
