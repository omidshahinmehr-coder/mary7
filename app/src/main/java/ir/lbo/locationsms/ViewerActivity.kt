package ir.lbo.locationsms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Viewer dashboard: shows a selector for which tracker ("car") is
 * currently active, that tracker's message history, and direct command
 * buttons that all act on whichever tracker is selected. Multiple trackers
 * can be defined from "Manage Trackers", each with its own name, phone
 * number, and optional command PIN.
 */
class ViewerActivity : LockProtectedActivity() {

    private lateinit var historyListView: ListView
    private lateinit var historyAdapter: ViewerHistoryAdapter
    private lateinit var trackerSpinner: Spinner
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var profiles: List<TrackerProfile> = emptyList()
    private var suppressSpinnerCallback = false

    private val refreshListener: () -> Unit = { runOnUiThread { refreshAll() } }

    private val requiredPermissions: Array<String> by lazy {
        val list = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!results.values.all { it }) {
                Toast.makeText(
                    this,
                    getString(R.string.viewer_permissions_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        TrackerProfileStore.migrateFromLegacySettingsIfNeeded(this)

        historyListView = findViewById(R.id.historyListView)
        historyAdapter = ViewerHistoryAdapter(this, emptyList())
        historyListView.adapter = historyAdapter
        historyListView.emptyView = findViewById<TextView>(R.id.emptyHistoryText)

        trackerSpinner = findViewById(R.id.trackerSpinner)
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        trackerSpinner.adapter = spinnerAdapter
        trackerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallback) return
                profiles.getOrNull(position)?.let {
                    TrackerProfileStore.setSelectedId(this@ViewerActivity, it.id)
                    refreshAll()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.manageTrackersButton).setOnClickListener {
            startActivity(Intent(this, TrackerProfilesActivity::class.java))
        }

        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener { onClearHistoryClicked() }
        findViewById<Button>(R.id.sendlocButton).setOnClickListener { sendCommand("sendloc") }
        findViewById<Button>(R.id.sendlogButton).setOnClickListener { sendCommand("sendlog") }
        findViewById<Button>(R.id.dellogButton).setOnClickListener { sendCommand("dellog") }
        findViewById<Button>(R.id.pingButton).setOnClickListener { sendCommand("ping") }
        findViewById<Button>(R.id.showMapButton).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        findViewById<Button>(R.id.trackerSettingsButton).setOnClickListener {
            startActivity(Intent(this, ViewerTrackerSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.exportHistoryButton).setOnClickListener { onExportHistoryClicked() }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        NewMessageNotifier.addListener(refreshListener)
    }

    override fun onPause() {
        super.onPause()
        NewMessageNotifier.removeListener(refreshListener)
    }

    private fun refreshAll() {
        profiles = TrackerProfileStore.getAll(this)
        val selectedId = TrackerProfileStore.getSelectedId(this)
        val selectedIndex = profiles.indexOfFirst { it.id == selectedId }.let { if (it < 0) 0 else it }

        suppressSpinnerCallback = true
        spinnerAdapter.clear()
        spinnerAdapter.addAll(profiles.map { it.name })
        spinnerAdapter.notifyDataSetChanged()
        if (profiles.isNotEmpty()) {
            trackerSpinner.setSelection(selectedIndex)
        }
        suppressSpinnerCallback = false

        val selected = profiles.getOrNull(selectedIndex)
        val historyEntries = if (selected != null) {
            ViewerHistoryStore.getAllForTracker(this, selected.id)
        } else {
            emptyList()
        }
        historyAdapter.updateItems(historyEntries)
    }

    private fun selectedProfile(): TrackerProfile? = TrackerProfileStore.getSelected(this)

    private fun onClearHistoryClicked() {
        val selected = selectedProfile()
        if (selected == null) {
            Toast.makeText(this, getString(R.string.viewer_no_tracker_selected), Toast.LENGTH_SHORT).show()
            return
        }
        ViewerHistoryStore.clearForTracker(this, selected.id)
        refreshAll()
        Toast.makeText(this, getString(R.string.viewer_history_cleared_toast), Toast.LENGTH_SHORT).show()
    }

    private fun sendCommand(command: String) {
        val selected = selectedProfile()
        if (selected == null) {
            Toast.makeText(this, getString(R.string.viewer_no_tracker_selected), Toast.LENGTH_LONG).show()
            return
        }
        if (!hasAllPermissions()) {
            Toast.makeText(this, getString(R.string.viewer_permissions_needed), Toast.LENGTH_SHORT).show()
            permissionLauncher.launch(requiredPermissions)
            return
        }

        val finalCommand = if (!selected.pin.isNullOrBlank()) "${selected.pin} $command" else command
        CommandSender.send(this, selected.phone, finalCommand)
        Toast.makeText(this, getString(R.string.viewer_command_sent_toast, command), Toast.LENGTH_SHORT).show()
    }

    /**
     * Builds a CSV of the selected tracker's history and hands it to the
     * system share sheet via a FileProvider content:// URI (a raw file://
     * path is blocked by newer Android versions for security reasons).
     */
    private fun onExportHistoryClicked() {
        val selected = selectedProfile()
        if (selected == null) {
            Toast.makeText(this, getString(R.string.viewer_no_tracker_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val entries = ViewerHistoryStore.getAllForTracker(this, selected.id)
        if (entries.isEmpty()) {
            Toast.makeText(this, getString(R.string.viewer_export_empty), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val csv = buildString {
                append("timestamp,text,latitude,longitude,is_alert\n")
                entries.forEach { entry ->
                    val timestamp = dateFormat.format(Date(entry.timestamp))
                    val safeText = entry.rawText.replace("\"", "'").replace("\n", " ")
                    append("\"$timestamp\",\"$safeText\",")
                    append("${entry.latitude ?: ""},${entry.longitude ?: ""},${entry.isAlert}\n")
                }
            }

            val safeName = selected.name.replace(Regex("[^A-Za-z0-9آ-ی_-]"), "_")
            val file = File(cacheDir, "viewer_history_${safeName}.csv")
            file.writeText(csv)

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.viewer_export_history_button)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.viewer_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
