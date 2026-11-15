package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.ScreenSnapshot

/** Skips a snapshot whose structure equals the previous one kept for the same package. */
class SnapshotDeduper {
    private val lastSignatureByPackage = mutableMapOf<String, Int>()

    /** True if [snapshot] differs from the last one for its package (and remembers it). */
    fun isNew(snapshot: ScreenSnapshot): Boolean {
        val signature = StructureSignature.of(snapshot)
        return lastSignatureByPackage.put(snapshot.packageName, signature) != signature
    }
}
