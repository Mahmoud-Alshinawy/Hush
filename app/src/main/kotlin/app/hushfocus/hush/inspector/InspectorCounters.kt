package app.hushfocus.hush.inspector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory counters shown on the Inspector screen. They prove the service reads no
 * window trees while the Inspector is off: supported-app events go up, tree reads don't.
 * Reset when the process restarts.
 */
object InspectorCounters {
    private val _supportedEvents = MutableStateFlow(0L)
    private val _treeReads = MutableStateFlow(0L)
    private val _saved = MutableStateFlow(0L)

    val supportedEvents: StateFlow<Long> = _supportedEvents.asStateFlow()
    val treeReads: StateFlow<Long> = _treeReads.asStateFlow()
    val saved: StateFlow<Long> = _saved.asStateFlow()

    fun supportedEventSeen() = _supportedEvents.update { it + 1 }

    fun treeRead() = _treeReads.update { it + 1 }

    fun entrySaved() = _saved.update { it + 1 }
}
