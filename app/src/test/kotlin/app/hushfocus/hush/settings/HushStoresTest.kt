package app.hushfocus.hush.settings

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HushStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun getTwiceReturnsTheSameStores() {
        val first = HushStores.get(folder.root)
        val second = HushStores.get(folder.root)
        assertSame(first, second)
        assertSame(first.settings, second.settings)
        assertSame(first.allowance, second.allowance)
        assertNotSame<Any>(first.settings, first.allowance)
    }
}
