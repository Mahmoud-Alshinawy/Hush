package app.hushfocus.hush.core

class GlobalPolicy(private val instagramPolicy: InstagramPolicy = InstagramPolicy()) {
    fun decide(snapshot: PolicySnapshot): PolicyDecision {
        if (!snapshot.settings.hushEnabled) return PolicyDecision()

        val controls = snapshot.controls
        val reason = when {
            controls.sleepBlocksApp -> BlockReason.SLEEP
            controls.downtimeBlocksApp -> BlockReason.DOWNTIME
            controls.dailyAppLimitReached -> BlockReason.DAILY_APP_LIMIT
            else -> null
        }
        if (reason != null) {
            return PolicyDecision(
                listOf(Action.HideAllCovers, Action.ReleaseAudioFocus, Action.ShowBlockScreen(reason)),
            )
        }
        if (controls.pauseActive) {
            return PolicyDecision(listOf(Action.HideAllCovers, Action.ReleaseAudioFocus))
        }

        val decision = when (snapshot.app) {
            AppId.INSTAGRAM -> instagramPolicy.decide(snapshot.instagram)
            else -> PolicyDecision()
        }
        val elapsed = snapshot.msSinceLastNavigationAction ?: return decision
        if (elapsed >= 1_000 || decision.actions.none { it.isNavigation() }) return decision

        val cooldownRemaining = 1_000 - elapsed
        return PolicyDecision(
            actions = decision.actions.filterNot { it.isNavigation() },
            recheckAfterMs = cooldownRemaining,
        )
    }
}
