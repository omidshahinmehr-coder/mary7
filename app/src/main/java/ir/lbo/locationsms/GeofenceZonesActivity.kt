package ir.lbo.locationsms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Lets the Viewer/Tracker define several additional geofence zones, each
 * with its own name, center, and radius — set either from the phone's
 * current location or by picking a point on a map.
 */
class GeofenceZonesActivity : LockProtectedActivity() {

    private lateinit var adapter: GeofenceZoneAdapter

    private val mapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val lat = result.data?.getDoubleExtra(GeofencePickerActivity.EXTRA_PICKED_LAT, Double.NaN)
                val lng = result.data?.getDoubleExtra(GeofencePickerActivity.EXTRA_PICKED_LNG, Double.NaN)
                if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) {
                    showNameDialogAndSave(lat, lng)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geofence_zones)

        val listView = findViewById<ListView>(R.id.zonesListView)
        adapter = GeofenceZoneAdapter(this, GeofenceZoneStore.getAll(this)) { refreshList() }
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.zonesEmptyText)

        findViewById<Button>(R.id.addZoneButton).setOnClickListener { onAddZoneClicked() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.updateItems(GeofenceZoneStore.getAll(this))
    }

    private fun onAddZoneClicked() {
        AlertDialog.Builder(this)
            .setTitle(R.string.geofence_zones_add_button)
            .setItems(
                arrayOf(
                    getString(R.string.geofence_source_current_location),
                    getString(R.string.geofence_pick_on_map_button)
                )
            ) { _, which ->
                if (which == 0) useCurrentLocationForNewZone() else mapPickerLauncher.launch(
                    Intent(this, GeofencePickerActivity::class.java)
                )
            }
            .show()
    }

    private fun useCurrentLocationForNewZone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, getString(R.string.geofence_zones_permission_needed), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.geofence_zones_getting_location), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(this@GeofenceZonesActivity)
            if (location == null) {
                Toast.makeText(
                    this@GeofenceZonesActivity,
                    getString(R.string.geofence_zones_location_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            showNameDialogAndSave(location.latitude, location.longitude)
        }
    }

    private fun showNameDialogAndSave(lat: Double, lng: Double) {
        val input = EditText(this).apply {
            hint = getString(R.string.geofence_zone_name_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.geofence_zones_add_button)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifEmpty {
                    getString(R.string.geofence_zone_default_name)
                }
                GeofenceZoneStore.add(this, name, lat, lng, 500L)
                refreshList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
