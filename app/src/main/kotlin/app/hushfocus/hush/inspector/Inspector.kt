package app.hushfocus.hush.inspector

import android.content.Context
import java.io.File

/** Shares one InspectorStore between the service and the UI (same process). */
object Inspector {
    @Volatile
    private var store: InspectorStore? = null

    fun store(context: Context): InspectorStore =
        store ?: synchronized(this) {
            store ?: InspectorStore(File(context.applicationContext.filesDir, "inspector")).also { store = it }
        }
}
