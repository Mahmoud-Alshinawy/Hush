package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverRect
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverLayoutTest {
    @Test
    fun screenRectMapsToPositionAndSize() {
        assertEquals(CoverLayout(x = 10, y = 2200, width = 270, height = 137), CoverRect(10, 2200, 280, 2337).toLayout())
    }

    @Test
    fun rectAtOriginIncludesStatusBarArea() {
        assertEquals(CoverLayout(x = 0, y = 0, width = 1080, height = 132), CoverRect(0, 0, 1080, 132).toLayout())
    }
}
