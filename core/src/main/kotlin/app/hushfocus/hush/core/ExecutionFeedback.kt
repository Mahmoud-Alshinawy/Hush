package app.hushfocus.hush.core

/** Report only actions the executor actually attempted, never cooldown-suppressed requests. */
data class ActionExecutionFeedback(val atMs: Long, val action: Action, val succeeded: Boolean)

/** Authoritative executor-owned state, not an assumption that requested actions succeeded. */
data class ExecutedCoverState(
    val visibleCovers: Map<CoverKind, CoverRect> = emptyMap(),
    val audioFocusHeld: Boolean = false,
)
