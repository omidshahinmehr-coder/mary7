package ir.lbo.locationsms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrackerCommandLogEntry(
    val timestamp: Long,
    val sender: String,
    val command: String,
    val result: String
)

object TrackerCommandLogStore {
    private const val KEY_LOG = "tracker_command_log_entries"
    private const val MAX_ENTRIES = 200

    fun addEntry(context: Context, sender: String, command: String, result: String) {
        val list = getAll(context).toMutableList()
        list.add(0, TrackerCommandLogEntry(System.currentTimeMillis(), sender, command, result))
        if (list.size > MAX_ENTRIES) {
            list.subList(MAX_ENTRIES, list.size).clear()
        }
        saveAll(context, list)
    }

    fun getAll(context: Context): List<TrackerCommandLogEntry> {
        val prefs = SecurePrefs.get(context)
        val raw = prefs.getString(KEY_LOG, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TrackerCommandLogEntry(
                    timestamp = obj.getLong("timestamp"),
                    sender = obj.getString("sender"),
                    command = obj.getString("command"),
                    result = obj.optString("result", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        SecurePrefs.get(context)
            .edit()
            .remove(KEY_LOG)
            .apply()
    }

    private fun saveAll(context: Context, list: List<TrackerCommandLogEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("timestamp", entry.timestamp)
            obj.put("sender", entry.sender)
            obj.put("command", entry.command)
            obj.put("result", entry.result)
            array.put(obj)
        }
        SecurePrefs.get(context)
            .edit()
            .putString(KEY_LOG, array.toString())
            .apply()
    }
}
