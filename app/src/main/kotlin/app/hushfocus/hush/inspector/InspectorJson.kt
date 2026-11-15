package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.CollectionInfo
import app.hushfocus.hush.inspector.model.CollectionItemInfo
import app.hushfocus.hush.inspector.model.InspectorEntry
import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.NodeInfo
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.ScrollRecord
import app.hushfocus.hush.inspector.model.WindowInfo
import org.json.JSONArray
import org.json.JSONObject

/** Encodes Inspector entries as one-line JSON (platform org.json) and decodes them back. */
object InspectorJson {
    private const val VERSION = 1

    fun encode(entry: InspectorEntry): String = toJson(entry).toString()

    fun decode(line: String): InspectorEntry = fromJson(JSONObject(line))

    fun toJson(entry: InspectorEntry): JSONObject = when (entry) {
        is ScreenSnapshot -> JSONObject()
            .put("kind", "snapshot")
            .put("v", VERSION)
            .put("timeMs", entry.timeMs)
            .put("package", entry.packageName)
            .put("trigger", entry.trigger)
            .put("nodeCapHit", entry.nodeCapHit)
            .put("depthCapHit", entry.depthCapHit)
            .put("windows", JSONArray(entry.windows.map(::windowToJson)))
            .put("nodes", JSONArray(entry.nodes.map(::nodeToJson)))

        is ScrollRecord -> JSONObject()
            .put("kind", "scroll")
            .put("v", VERSION)
            .put("timeMs", entry.timeMs)
            .put("package", entry.packageName)
            .putOpt("sourceId", entry.sourceId)
            .putOpt("sourceClass", entry.sourceClass)
            .putOpt("bounds", entry.bounds?.let(::boundsToJson))
            .put("fromIndex", entry.fromIndex)
            .put("toIndex", entry.toIndex)
            .put("itemCount", entry.itemCount)
            .put("scrollDeltaX", entry.scrollDeltaX)
            .put("scrollDeltaY", entry.scrollDeltaY)
            .put("events", entry.events)
    }

    private fun fromJson(json: JSONObject): InspectorEntry = when (val kind = json.getString("kind")) {
        "snapshot" -> ScreenSnapshot(
            timeMs = json.getLong("timeMs"),
            packageName = json.getString("package"),
            trigger = json.getString("trigger"),
            windows = json.getJSONArray("windows").objects().map(::windowFromJson),
            nodes = json.getJSONArray("nodes").objects().map(::nodeFromJson),
            nodeCapHit = json.getBoolean("nodeCapHit"),
            depthCapHit = json.getBoolean("depthCapHit"),
        )

        "scroll" -> ScrollRecord(
            timeMs = json.getLong("timeMs"),
            packageName = json.getString("package"),
            sourceId = json.stringOrNull("sourceId"),
            sourceClass = json.stringOrNull("sourceClass"),
            bounds = json.optJSONArray("bounds")?.let(::boundsFromJson),
            fromIndex = json.getInt("fromIndex"),
            toIndex = json.getInt("toIndex"),
            itemCount = json.getInt("itemCount"),
            scrollDeltaX = json.getInt("scrollDeltaX"),
            scrollDeltaY = json.getInt("scrollDeltaY"),
            events = json.getInt("events"),
        )

        else -> throw IllegalArgumentException("Unknown entry kind: $kind")
    }

    private fun windowToJson(window: WindowInfo): JSONObject = JSONObject()
        .put("type", window.type)
        .put("layer", window.layer)
        .put("focused", window.focused)
        .put("active", window.active)
        .put("bounds", boundsToJson(window.bounds))
        .putOpt("title", window.title)

    private fun windowFromJson(json: JSONObject) = WindowInfo(
        type = json.getString("type"),
        layer = json.getInt("layer"),
        focused = json.getBoolean("focused"),
        active = json.getBoolean("active"),
        bounds = boundsFromJson(json.getJSONArray("bounds")),
        title = json.stringOrNull("title"),
    )

    private fun nodeToJson(node: NodeInfo): JSONObject = JSONObject()
        .put("depth", node.depth)
        .putOpt("id", node.viewId)
        .putOpt("class", node.className)
        .put("bounds", boundsToJson(node.bounds))
        .put("childCount", node.childCount)
        .put("flags", JSONArray(node.flags.map { it.key }))
        .putOpt("text", node.text)
        .putOpt("desc", node.contentDescription)
        .putOpt("hint", node.hintText)
        .putOpt("paneTitle", node.paneTitle)
        .putOpt("state", node.stateDescription)
        .putOpt(
            "collectionInfo",
            node.collectionInfo?.let { JSONObject().put("rows", it.rows).put("cols", it.cols) },
        )
        .putOpt(
            "collectionItemInfo",
            node.collectionItemInfo?.let { JSONObject().put("row", it.rowIndex).put("col", it.colIndex) },
        )

    private fun nodeFromJson(json: JSONObject) = NodeInfo(
        depth = json.getInt("depth"),
        viewId = json.stringOrNull("id"),
        className = json.stringOrNull("class"),
        bounds = boundsFromJson(json.getJSONArray("bounds")),
        childCount = json.getInt("childCount"),
        flags = json.getJSONArray("flags").strings().map(NodeFlag::fromKey).toSet(),
        text = json.stringOrNull("text"),
        contentDescription = json.stringOrNull("desc"),
        hintText = json.stringOrNull("hint"),
        paneTitle = json.stringOrNull("paneTitle"),
        stateDescription = json.stringOrNull("state"),
        collectionInfo = json.optJSONObject("collectionInfo")
            ?.let { CollectionInfo(it.getInt("rows"), it.getInt("cols")) },
        collectionItemInfo = json.optJSONObject("collectionItemInfo")
            ?.let { CollectionItemInfo(it.getInt("row"), it.getInt("col")) },
    )

    private fun boundsToJson(b: Bounds) = JSONArray(listOf(b.left, b.top, b.right, b.bottom))

    private fun boundsFromJson(a: JSONArray) = Bounds(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))

    private fun JSONObject.stringOrNull(key: String): String? = if (has(key)) getString(key) else null

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
