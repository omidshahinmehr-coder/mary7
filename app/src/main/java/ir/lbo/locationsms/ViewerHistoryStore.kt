package ir.lbo.locationsms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ViewerHistoryEntry(
    val timestamp: Long,
    val rawText: String,
    val latitude: Double?,
    val longitude: Double?,
    val isAlert: Boolean = false,
    val trackerId: String? = null // which tracker profile this message came from
)

object ViewerHistoryStore {
    private const val KEY_HISTORY = "viewer_history_entries"
    private const val MAX_ENTRIES = 300

    fun addEntry(context: Context, entry: ViewerHistoryEntry) {
        val list = getAll(context).toMutableList()
        list.add(0, entry) // newest first
        if (list.size > MAX_ENTRIES) {
            list.subList(MAX_ENTRIES, list.size).clear()
        }
        saveAll(context, list)
    }

    fun getAll(context: Context): List<ViewerHistoryEntry> {
        val prefs = SecurePrefs.get(context)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ViewerHistoryEntry(
                    timestamp = obj.getLong("timestamp"),
                    rawText = obj.getString("rawText"),
                    latitude = if (obj.has("lat")) obj.getDouble("lat") else null,
                    longitude = if (obj.has("lng")) obj.getDouble("lng") else null,
                    isAlert = obj.optBoolean("isAlert", false),
                    trackerId = if (obj.has("trackerId") && !obj.isNull("trackerId")) obj.getString("trackerId") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Only the messages that came from one specific tracker profile. */
    fun getAllForTracker(context: Context, trackerId: String): List<ViewerHistoryEntry> =
        getAll(context).filter { it.trackerId == trackerId }

    fun clear(context: Context) {
        SecurePrefs.get(context)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    /** Clears only the messages belonging to one tracker profile, leaving
     * other trackers' history untouched. */
    fun clearForTracker(context: Context, trackerId: String) {
        val remaining = getAll(context).filter { it.trackerId != trackerId }
        saveAll(context, remaining)
    }

    /** One-time migration helper: tags any pre-existing entries that don't
     * belong to a tracker yet (from before multi-tracker support existed)
     * with the given tracker id, so old history doesn't appear to vanish. */
    fun assignUntaggedEntriesToTracker(context: Context, trackerId: String) {
        val updated = getAll(context).map {
            if (it.trackerId == null) it.copy(trackerId = trackerId) else it
        }
        saveAll(context, updated)
    }

    private fun saveAll(context: Context, list: List<ViewerHistoryEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("timestamp", entry.timestamp)
            obj.put("rawText", entry.rawText)
            entry.latitude?.let { obj.put("lat", it) }
            entry.longitude?.let { obj.put("lng", it) }
            obj.put("isAlert", entry.isAlert)
            if (entry.trackerId != null) obj.put("trackerId", entry.trackerId)
            array.put(obj)
        }
        SecurePrefs.get(context)
            .edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }
}
