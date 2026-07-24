package ir.lbo.locationsms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Lets the Viewer change every SMS-driven Tracker setting: autosend,
 * movement alert, geofence, low-battery alert, admin/autosend number
 * lists, and the log-save timer. Every action here composes and sends one
 * SMS command to whichever tracker profile is currently selected on the
 * main Viewer screen. If that tracker has its own command PIN configured,
 * it's transparently prepended to every outgoing command.
 */
class ViewerTrackerSettingsActivity : LockProtectedActivity() {

    private lateinit var trackerPhoneInput: EditText
    private lateinit var commandPinInput: EditText
    private lateinit var activeTrackerNameText: TextView
    private var activeProfile: TrackerProfile? = null

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
            if (results.values.all { it }) {
                Toast.makeText(this, getString(R.string.viewer_settings_permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.viewer_settings_permissions_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer_tracker_settings)

        trackerPhoneInput = findViewById(R.id.trackerPhoneInput)
        commandPinInput = findViewById(R.id.commandPinInput)
        activeTrackerNameText = findViewById(R.id.activeTrackerNameText)

        loadActiveProfile()

        findViewById<Button>(R.id.saveTrackerPhoneButton).setOnClickListener { onSaveTrackerPhone() }
        findViewById<Button>(R.id.saveCommandPinButton).setOnClickListener { onSaveCommandPin() }

        val autosendIntervalInput = findViewById<EditText>(R.id.autosendIntervalInput)
        findViewById<Button>(R.id.autosendOnButton).setOnClickListener {
            val minutes = parsedNumber(autosendIntervalInput) ?: 15L
            val safe = if (minutes < 15) 15L else minutes
            autosendIntervalInput.setText(safe.toString())
            sendCommand("autosend $safe on")
        }
        findViewById<Button>(R.id.autosendOffButton).setOnClickListener { sendCommand("autosend off") }

        val moveAlertDistanceInput = findViewById<EditText>(R.id.moveAlertDistanceInput)
        findViewById<Button>(R.id.moveAlertOnButton).setOnClickListener {
            val distance = parsedNumber(moveAlertDistanceInput) ?: 50L
            val safe = if (distance < 0) 0L else distance
            moveAlertDistanceInput.setText(safe.toString())
            sendCommand("MoveAlert $safe on")
        }
        findViewById<Button>(R.id.moveAlertOffButton).setOnClickListener { sendCommand("MoveAlert off") }

        val geofenceRadiusInput = findViewById<EditText>(R.id.geofenceRadiusInput)
        findViewById<Button>(R.id.geofenceOnButton).setOnClickListener {
            val radius = parsedNumber(geofenceRadiusInput) ?: 500L
            val safe = if (radius < 50) 50L else radius
            geofenceRadiusInput.setText(safe.toString())
            sendCommand("Geofence $safe on")
        }
        findViewById<Button>(R.id.geofenceOffButton).setOnClickListener { sendCommand("Geofence off") }

        val lowBatteryPercentInput = findViewById<EditText>(R.id.lowBatteryPercentInput)
        findViewById<Button>(R.id.lowBatteryOnButton).setOnClickListener {
            val percent = parsedNumber(lowBatteryPercentInput) ?: 15L
            val safe = percent.coerceIn(1L, 90L)
            lowBatteryPercentInput.setText(safe.toString())
            sendCommand("LowbatteryAlert $safe on")
        }
        findViewById<Button>(R.id.lowBatteryOffButton).setOnClickListener { sendCommand("LowbatteryAlert off") }

        val adminNumbersInput = findViewById<EditText>(R.id.adminNumbersInput)
        findViewById<Button>(R.id.setAdminNumbersButton).setOnClickListener {
            val numbers = adminNumbersInput.text.toString().trim()
            if (numbers.isEmpty()) {
                Toast.makeText(this, getString(R.string.viewer_settings_error_numbers_needed), Toast.LENGTH_SHORT).show()
            } else {
                sendCommand("SetAdminNumbers $numbers")
            }
        }

        val autosendNumbersInput = findViewById<EditText>(R.id.autosendNumbersInput)
        findViewById<Button>(R.id.setAutosendNumbersButton).setOnClickListener {
            val numbers = autosendNumbersInput.text.toString().trim()
            if (numbers.isEmpty()) {
                Toast.makeText(this, getString(R.string.viewer_settings_error_numbers_needed), Toast.LENGTH_SHORT).show()
            } else {
                sendCommand("SetAutosendNumbers $numbers")
            }
        }

        val logTimerInput = findViewById<EditText>(R.id.logTimerInput)
        findViewById<Button>(R.id.setLogTimerButton).setOnClickListener {
            val minutes = parsedNumber(logTimerInput) ?: 15L
            val safe = if (minutes < 15) 15L else minutes
            logTimerInput.setText(safe.toString())
            sendCommand("SetLogTimer $safe")
        }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        loadActiveProfile()
    }

    /** (Re)loads whichever tracker is currently selected on the main
     * Viewer screen, and pre-fills the phone/PIN fields with its values. */
    private fun loadActiveProfile() {
        val profile = TrackerProfileStore.getSelected(this)
        activeProfile = profile

        if (profile == null) {
            activeTrackerNameText.text = getString(R.string.viewer_no_tracker_selected)
            trackerPhoneInput.setText("")
            commandPinInput.setText("")
            return
        }

        activeTrackerNameText.text = getString(R.string.viewer_settings_editing_tracker, profile.name)
        trackerPhoneInput.setText(profile.phone)
        commandPinInput.setText(profile.pin ?: "")
    }

    /** Reads an EditText, normalizing Persian/Arabic-Indic digits first. */
    private fun parsedNumber(input: EditText): Long? =
        PersianDigits.toEnglish(input.text.toString().trim()).toLongOrNull()

    private fun onSaveTrackerPhone() {
        val profile = activeProfile
        if (profile == null) {
            Toast.makeText(this, getString(R.string.viewer_no_tracker_selected), Toast.LENGTH_SHORT).show()
            return
        }
        val phone = trackerPhoneInput.text.toString().trim()
        if (phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.viewer_settings_error_phone_needed), Toast.LENGTH_SHORT).show()
            return
        }
        TrackerProfileStore.update(this, profile.id, profile.name, phone, profile.pin)
        loadActiveProfile()
        Toast.makeText(this, getString(R.string.viewer_settings_phone_saved), Toast.LENGTH_SHORT).show()

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun onSaveCommandPin() {
        val profile = activeProfile
        if (profile == null) {
            Toast.makeText(this, getString(R.string.viewer_no_tracker_selected), Toast.LENGTH_SHORT).show()
            return
        }
        val pin = PersianDigits.toEnglish(commandPinInput.text.toString().trim())
        TrackerProfileStore.update(this, profile.id, profile.name, profile.phone, pin)
        loadActiveProfile()
        Toast.makeText(
            this,
            if (pin.isEmpty()) getString(R.string.viewer_settings_pin_removed) else getString(R.string.viewer_settings_pin_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun sendCommand(command: String) {
        val profile = activeProfile
        if (profile == null) {
            Toast.makeText(this, getString(R.string.viewer_settings_error_no_phone), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasAllPermissions()) {
            Toast.makeText(this, getString(R.string.viewer_settings_error_no_permission), Toast.LENGTH_SHORT).show()
            permissionLauncher.launch(requiredPermissions)
            return
        }

        val finalCommand = if (!profile.pin.isNullOrBlank()) "${profile.pin} $command" else command

        CommandSender.send(this, profile.phone, finalCommand)
        Toast.makeText(this, getString(R.string.viewer_settings_command_sent_toast, command), Toast.LENGTH_SHORT).show()
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
