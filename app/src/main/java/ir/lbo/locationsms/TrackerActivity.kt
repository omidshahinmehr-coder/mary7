package ir.lbo.locationsms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/**
 * Main Tracker dashboard, split in two parts:
 *  1) A log-file viewer with a "Browse" button to pick any CSV log file
 *     (the current active log, or an archived one) and show its content.
 *  2) A button that opens TrackerSettingsActivity, where all of the
 *     tracker's current settings live and can be changed.
 */
class TrackerActivity : LockProtectedActivity() {

    private lateinit var logFileNameText: TextView
    private lateinit var logFileContentText: TextView

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadLogFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker)

        logFileNameText = findViewById(R.id.logFileNameText)
        logFileContentText = findViewById(R.id.logFileContentText)

        findViewById<Button>(R.id.browseLogButton).setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream"))
        }

        findViewById<Button>(R.id.openTrackerSettingsButton).setOnClickListener {
            startActivity(Intent(this, TrackerSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.openCommandHistoryButton).setOnClickListener {
            startActivity(Intent(this, TrackerCommandHistoryActivity::class.java))
        }

        findViewById<Button>(R.id.tripStatsButton).setOnClickListener { onTripStatsClicked() }

        loadDefaultActiveLog()
    }

    /**
     * Computes and shows basic trip statistics (distance, duration, average
     * and max speed) from whatever CSV is currently displayed in the log
     * box above — reusing data that's already being logged rather than
     * tracking anything new.
     */
    private fun onTripStatsClicked() {
        val stats = TripStatsCalculator.calculate(logFileContentText.text.toString())
        if (stats == null) {
            Toast.makeText(this, getString(R.string.tracker_trip_stats_not_enough_data), Toast.LENGTH_SHORT).show()
            return
        }

        val totalHours = stats.durationMillis / 3_600_000
        val remainingMinutes = (stats.durationMillis % 3_600_000) / 60_000
        val durationText = getString(R.string.tracker_trip_duration_format, totalHours, remainingMinutes)

        val message = getString(
            R.string.tracker_trip_stats_content,
            stats.pointCount,
            String.format(Locale.US, "%.1f", stats.totalDistanceMeters / 1000.0),
            durationText,
            String.format(Locale.US, "%.1f", stats.averageSpeedKmh),
            String.format(Locale.US, "%.1f", stats.maxSpeedKmh)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.tracker_trip_stats_button)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun loadDefaultActiveLog() {
        val file = LocationLogger.getLogFile(this)
        if (!file.exists()) {
            logFileNameText.text = getString(R.string.tracker_log_no_file_yet)
            logFileContentText.text = "—"
            return
        }
        try {
            logFileNameText.text = getString(R.string.tracker_log_active_file, file.name)
            logFileContentText.text = file.readText().ifBlank { "—" }
        } catch (e: Exception) {
            logFileNameText.text = getString(R.string.tracker_log_read_error)
            logFileContentText.text = "—"
        }
    }

    private fun loadLogFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                logFileNameText.text = getString(
                    R.string.tracker_log_selected_file,
                    queryDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()
                )
                logFileContentText.text = text.ifBlank { "—" }
            } ?: run {
                Toast.makeText(this, getString(R.string.tracker_log_open_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.tracker_log_selected_read_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
