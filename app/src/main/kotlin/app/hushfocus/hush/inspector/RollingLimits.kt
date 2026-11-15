package app.hushfocus.hush.inspector

/**
 * Size limits for the Inspector log. The log is appended to line by line and only
 * rewritten once it goes over 110% of a limit, when it is trimmed back to the limit
 * by dropping the oldest lines. This keeps storage writes small.
 */
class RollingLimits(
    val maxCount: Int = 300,
    val maxBytes: Long = 5L * 1024 * 1024,
) {
    fun needsTrim(count: Int, bytes: Long): Boolean =
        count > maxCount * 11 / 10 || bytes > maxBytes * 11 / 10

    /** Drops the oldest lines until both limits hold. */
    fun trim(lines: List<String>): List<String> {
        var bytes = lines.sumOf { lineBytes(it) }
        var start = 0
        while (start < lines.size && (lines.size - start > maxCount || bytes > maxBytes)) {
            bytes -= lineBytes(lines[start])
            start++
        }
        return lines.subList(start, lines.size)
    }

    companion object {
        /** Bytes a line takes in the file: UTF-8 plus the newline. */
        fun lineBytes(line: String): Long = line.toByteArray(Charsets.UTF_8).size + 1L
    }
}
