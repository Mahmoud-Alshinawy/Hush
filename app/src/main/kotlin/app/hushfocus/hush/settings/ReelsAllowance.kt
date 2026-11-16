package app.hushfocus.hush.settings

/** The daily short-video allowance choices (spec §2.8). Off is null. */
object ReelsAllowance {
    val OPTIONS_MINUTES: List<Int> = listOf(5, 10, 15, 20, 30, 45, 60)

    /** A stored value outside the list reads as off: never guess a different amount. */
    fun fromStored(minutes: Int?): Int? = minutes?.takeIf { it in OPTIONS_MINUTES }

    fun isValid(minutes: Int?): Boolean = minutes == null || minutes in OPTIONS_MINUTES
}
