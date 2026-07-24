package ir.lbo.locationsms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GeofenceZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Long,
    val enabled: Boolean,
    val state: String? // "inside" / "outside" / null (not yet determined)
)

/**
 * The original single geofence (center/radius/enabled/state in
 * SettingsRepository) stays exactly as-is, since it's the one controlled
 * remotely by the "Geofence on/off" SMS command — changing its storage
 * shape would risk breaking that command's behavior.
 *
 * This store holds any *additional* zones (e.g. "home", "work"), which are
 * only managed locally from GeofenceZonesActivity on the Tracker phone —
 * they aren't individually addressable by SMS, keeping the SMS command
 * surface unchanged.
 */
object GeofenceZoneStore {
    private const val KEY_ZONES = "geofence_zones"

    fun getAll(context: Context): List<GeofenceZone> {
        val prefs = SecurePrefs.get(context)
        val raw = prefs.getString(KEY_ZONES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                GeofenceZone(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    radiusMeters = obj.getLong("radiusMeters"),
                    enabled = obj.optBoolean("enabled", true),
                    state = if (obj.has("state") && !obj.isNull("state")) obj.getString("state") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, name: String, latitude: Double, longitude: Double, radiusMeters: Long) {
        val list = getAll(context).toMutableList()
        list.add(
            GeofenceZone(
                id = UUID.randomUUID().toString(),
                name = name,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                enabled = true,
                state = null
            )
        )
        saveAll(context, list)
    }

    fun updateEnabled(context: Context, id: String, enabled: Boolean) {
        val list = getAll(context).map { if (it.id == id) it.copy(enabled = enabled) else it }
        saveAll(context, list)
    }

    fun updateRadius(context: Context, id: String, radiusMeters: Long) {
        val list = getAll(context).map { if (it.id == id) it.copy(radiusMeters = radiusMeters) else it }
        saveAll(context, list)
    }

    fun updateState(context: Context, id: String, state: String) {
        val list = getAll(context).map { if (it.id == id) it.copy(state = state) else it }
        saveAll(context, list)
    }

    fun delete(context: Context, id: String) {
        val list = getAll(context).filter { it.id != id }
        saveAll(context, list)
    }

    private fun saveAll(context: Context, list: List<GeofenceZone>) {
        val array = JSONArray()
        list.forEach { zone ->
            val obj = JSONObject()
            obj.put("id", zone.id)
            obj.put("name", zone.name)
            obj.put("latitude", zone.latitude)
            obj.put("longitude", zone.longitude)
            obj.put("radiusMeters", zone.radiusMeters)
            obj.put("enabled", zone.enabled)
            if (zone.state != null) obj.put("state", zone.state)
            array.put(obj)
        }
        SecurePrefs.get(context).edit().putString(KEY_ZONES, array.toString()).apply()
    }
}
