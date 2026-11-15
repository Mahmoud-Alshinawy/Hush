package app.hushfocus.hush.core

data class InstagramSettings(
    val allowReelsSentInDms: Boolean = true,
    val homeFeedMode: HomeFeedMode = HomeFeedMode.NORMAL,
    val stopAtCaughtUp: Boolean = true,
    val hideExplore: Boolean = false,
    val hideStories: Boolean = false,
    val dmsOnly: Boolean = false,
)

/** Schedules, pause and limits are resolved by trackers before a policy check. */
data class GlobalSettings(val hushEnabled: Boolean = true)
