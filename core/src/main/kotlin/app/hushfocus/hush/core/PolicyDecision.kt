package app.hushfocus.hush.core

data class PolicyDecision(
    val actions: List<Action> = emptyList(),
    /** Schedule one fresh check after this delay; do not retain an action for replay. */
    val recheckAfterMs: Long? = null,
) {
    init {
        require(actions.count { it.isNavigation() } <= 1)
        require(recheckAfterMs == null || recheckAfterMs > 0)
    }
}
