package ir.lbo.locationsms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TrackerProfile(
    val id: String,
    val name: String,
    val phone: String,
    val pin: String? // per-tracker command PIN; null/blank = no PIN for this tracker
)

/**
 * Lets the Viewer manage several tracker phones ("cars") instead of just
 * one — each with its own name, phone number, and (optional) command PIN.
 * Exactly one profile is "selected" at a time; all direct commands and the
 * remote-settings screen act on whichever profile is currently selected.
 *
 * On first use after upgrading from the single-tracker version, the old
 * phone/PIN (still readable from SettingsRepository for this purpose only)
 * is migrated into a first profile automatically, and any existing
 * history entries are tagged with it, so nothing appears to vanish.
 */
object TrackerProfileStore {
    private const val KEY_PROFILES = "tracker_profiles"
    private const val KEY_SELECTED_ID = "tracker_profiles_selected_id"
    private const val KEY_MIGRATED = "tracker_profiles_migrated"

    fun getAll(context: Context): List<TrackerProfile> {
        val prefs = SecurePrefs.get(context)
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TrackerProfile(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    phone = obj.getString("phone"),
                    pin = if (obj.has("pin") && !obj.isNull("pin")) obj.getString("pin") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Adds a new profile and makes it the selected one; returns its id. */
    fun add(context: Context, name: String, phone: String, pin: String?): String {
        val list = getAll(context).toMutableList()
        val id = UUID.randomUUID().toString()
        list.add(TrackerProfile(id, name, phone, pin?.takeIf { it.isNotBlank() }))
        saveAll(context, list)
        setSelectedId(context, id)
        return id
    }

    fun update(context: Context, id: String, name: String, phone: String, pin: String?) {
        val list = getAll(context).map {
            if (it.id == id) it.copy(name = name, phone = phone, pin = pin?.takeIf { p -> p.isNotBlank() }) else it
        }
        saveAll(context, list)
    }

    fun delete(context: Context, id: String) {
        val list = getAll(context).filter { it.id != id }
        saveAll(context, list)
        if (getSelectedId(context) == id) {
            setSelectedId(context, list.firstOrNull()?.id)
        }
    }

    fun getSelectedId(context: Context): String? =
        SecurePrefs.get(context).getString(KEY_SELECTED_ID, null)

    fun setSelectedId(context: Context, id: String?) {
        SecurePrefs.get(context).edit().putString(KEY_SELECTED_ID, id).apply()
    }

    /** The currently selected profile, or the first available one if the
     * stored selection is missing/stale, or null if there are none yet. */
    fun getSelected(context: Context): TrackerProfile? {
        val all = getAll(context)
        if (all.isEmpty()) return null
        val selectedId = getSelectedId(context)
        return all.find { it.id == selectedId } ?: all.first()
    }

    /**
     * One-time migration from the old single-tracker settings (a single
     * phone number + PIN stored directly in SettingsRepository) into the
     * new profile list. Safe to call on every app start — it only acts
     * once, and only if there's legacy data and no profiles yet.
     */
    fun migrateFromLegacySettingsIfNeeded(context: Context) {
        val prefs = SecurePrefs.get(context)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()

        if (getAll(context).isNotEmpty()) return

        val settings = SettingsRepository(context)
        val legacyPhone = settings.getTrackerViewerPhone()
        if (legacyPhone.isNullOrBlank()) return

        val legacyPin = settings.getCommandPin()
        val id = add(context, context.getString(R.string.tracker_profile_default_name), legacyPhone, legacyPin)

        // Carry old history forward under the new profile instead of
        // letting it look like it disappeared.
        ViewerHistoryStore.assignUntaggedEntriesToTracker(context, id)
    }

    private fun saveAll(context: Context, list: List<TrackerProfile>) {
        val array = JSONArray()
        list.forEach { profile ->
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("phone", profile.phone)
            if (profile.pin != null) obj.put("pin", profile.pin)
            array.put(obj)
        }
        SecurePrefs.get(context).edit().putString(KEY_PROFILES, array.toString()).apply()
    }
}
