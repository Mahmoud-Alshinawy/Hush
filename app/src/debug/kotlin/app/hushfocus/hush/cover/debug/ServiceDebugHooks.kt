package app.hushfocus.hush.cover.debug

import app.hushfocus.hush.cover.CoverManager
import java.lang.ref.WeakReference

/**
 * Debug build only: lets CoverDemoActivity reach the running service's CoverManager.
 * Held weakly so the service can't leak. The release source set has a no-op version.
 */
object ServiceDebugHooks {
    @Volatile
    private var manager: WeakReference<CoverManager>? = null

    val coverManager: CoverManager? get() = manager?.get()

    fun onConnected(coverManager: CoverManager) {
        manager = WeakReference(coverManager)
    }

    fun onDisconnected() {
        manager = null
    }
}
