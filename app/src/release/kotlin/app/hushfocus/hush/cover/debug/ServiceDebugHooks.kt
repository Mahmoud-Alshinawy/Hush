package app.hushfocus.hush.cover.debug

import app.hushfocus.hush.cover.CoverManager

/** Release build: no debug hooks. The debug source set has the real version for the cover demo. */
object ServiceDebugHooks {
    fun onConnected(coverManager: CoverManager) = Unit

    fun onDisconnected() = Unit
}
