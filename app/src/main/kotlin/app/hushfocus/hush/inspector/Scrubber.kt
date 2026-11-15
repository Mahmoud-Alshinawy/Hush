package app.hushfocus.hush.inspector

/**
 * Replaces screen text with a length-only placeholder such as "‹text:12›", so usernames,
 * names, captions and messages are never stored. Values that exactly match the app's
 * label allow-list are kept.
 *
 * A value like "Reels, selected" is split at the first ", ": the label part is kept if
 * allow-listed, and the rest only if every ", "-separated piece is a known state
 * ("selected", "not selected", "tab N of M"). Otherwise the rest becomes a placeholder,
 * so "Profile, jane.doe" becomes "Profile, ‹text:8›".
 */
object Scrubber {
    private const val SEPARATOR = ", "
    private val STATE_PATTERN = Regex("selected|not selected|tab \\d+ of \\d+", RegexOption.IGNORE_CASE)

    fun scrub(value: String?, allowList: Set<String>): String? {
        if (value == null || value.isBlank()) return value
        if (value in allowList) return value

        val split = value.indexOf(SEPARATOR)
        if (split > 0) {
            val label = value.substring(0, split)
            if (label in allowList) {
                val rest = value.substring(split + SEPARATOR.length)
                return if (isStateSuffix(rest)) value else "$label$SEPARATOR${placeholder(rest)}"
            }
        }
        return placeholder(value)
    }

    private fun isStateSuffix(rest: String): Boolean =
        rest.split(SEPARATOR).all { STATE_PATTERN.matches(it) }

    private fun placeholder(value: String): String =
        "‹text:${value.codePointCount(0, value.length)}›"
}
