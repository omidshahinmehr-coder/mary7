package ir.lbo.locationsms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class GeofenceZoneAdapter(
    context: Context,
    private var items: List<GeofenceZone>,
    private val onChanged: () -> Unit
) : ArrayAdapter<GeofenceZone>(context, R.layout.item_geofence_zone, ArrayList(items)) {

    fun updateItems(newItems: List<GeofenceZone>) {
        items = newItems
        clear()
        addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_geofence_zone, parent, false)
        val zone = items[position]

        view.findViewById<TextView>(R.id.zoneNameText).text = zone.name
        view.findViewById<TextView>(R.id.zoneCoordinatesText).text = context.getString(
            R.string.geofence_zone_coordinates,
            String.format(Locale.US, "%.5f", zone.latitude),
            String.format(Locale.US, "%.5f", zone.longitude)
        )

        val enabledSwitch = view.findViewById<Switch>(R.id.zoneEnabledSwitch)
        enabledSwitch.setOnCheckedChangeListener(null)
        enabledSwitch.isChecked = zone.enabled
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            GeofenceZoneStore.updateEnabled(context, zone.id, isChecked)
            onChanged()
        }

        val radiusInput = view.findViewById<EditText>(R.id.zoneRadiusInput)
        radiusInput.setText(zone.radiusMeters.toString())

        view.findViewById<Button>(R.id.zoneSaveRadiusButton).setOnClickListener {
            val requested = PersianDigits.toEnglish(radiusInput.text.toString().trim()).toLongOrNull()
            if (requested == null || requested < 50) {
                Toast.makeText(context, context.getString(R.string.geofence_zone_radius_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GeofenceZoneStore.updateRadius(context, zone.id, requested)
            Toast.makeText(context, context.getString(R.string.geofence_zone_radius_saved), Toast.LENGTH_SHORT).show()
            onChanged()
        }

        view.findViewById<Button>(R.id.zoneDeleteButton).setOnClickListener {
            GeofenceZoneStore.delete(context, zone.id)
            onChanged()
        }

        return view
    }
}
