package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.InspectorEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

/**
 * The Inspector's rolling log: one JSON entry per line in [directory]/log.jsonl.
 *
 * Appends a single line per entry and rewrites the file only when [limits] say it must
 * be trimmed. Clear moves the log aside so it can be undone; entries captured while an
 * undo is pending go to a fresh log and stay after the restored ones.
 * All methods are synchronised: the service writes from its own thread, the UI from IO.
 */
class InspectorStore(
    private val directory: File,
    private val limits: RollingLimits = RollingLimits(),
) {
    private val log = File(directory, "log.jsonl")
    private val undo = File(directory, "log.jsonl.undo")

    private var count = -1
    private var bytes = 0L

    private val _version = MutableStateFlow(0L)

    /** Changes whenever the log changes, so the UI knows to reload. */
    val version: StateFlow<Long> = _version.asStateFlow()

    @Synchronized
    fun append(entry: InspectorEntry) {
        ensureSizeKnown()
        val line = InspectorJson.encode(entry)
        directory.mkdirs()
        log.appendText(line + "\n", Charsets.UTF_8)
        count++
        bytes += RollingLimits.lineBytes(line)
        if (limits.needsTrim(count, bytes)) rewrite(limits.trim(readLines(log)))
        changed()
    }

    /** All entries, oldest first. A line that can't be decoded is skipped. */
    @Synchronized
    fun readAll(): List<InspectorEntry> =
        readLines(log).mapNotNull { runCatching { InspectorJson.decode(it) }.getOrNull() }

    /** Moves the log aside; [undoClear] brings it back, [confirmClear] deletes it. */
    @Synchronized
    fun clear() {
        undo.delete()
        if (log.exists()) log.renameTo(undo)
        count = 0
        bytes = 0
        changed()
    }

    @Synchronized
    fun undoClear() {
        if (!undo.exists()) return
        rewrite(limits.trim(readLines(undo) + readLines(log)))
        undo.delete()
        changed()
    }

    @Synchronized
    fun confirmClear() {
        undo.delete()
    }

    /** Writes every entry as one JSON object; returns how many were written. */
    @Synchronized
    fun exportTo(out: OutputStream, exportedAtMs: Long): Int {
        val entries = readLines(log)
        val json = JSONObject()
            .put("app", "Hush")
            .put("exportedAtMs", exportedAtMs)
            .put("entries", JSONArray(entries.map(::JSONObject)))
        out.write(json.toString(1).toByteArray(Charsets.UTF_8))
        return entries.size
    }

    private fun ensureSizeKnown() {
        if (count >= 0) return
        val lines = readLines(log)
        count = lines.size
        bytes = lines.sumOf(RollingLimits::lineBytes)
    }

    private fun rewrite(lines: List<String>) {
        directory.mkdirs()
        val temp = File(directory, "log.jsonl.tmp")
        temp.writeText(lines.joinToString(separator = "") { it + "\n" }, Charsets.UTF_8)
        if (!temp.renameTo(log)) {
            log.delete()
            temp.renameTo(log)
        }
        count = lines.size
        bytes = lines.sumOf(RollingLimits::lineBytes)
    }

    private fun readLines(file: File): List<String> =
        if (file.exists()) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()

    private fun changed() {
        _version.value++
    }
}
