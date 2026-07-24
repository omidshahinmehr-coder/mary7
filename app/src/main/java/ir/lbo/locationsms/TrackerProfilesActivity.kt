package ir.lbo.locationsms

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Lets the Viewer define several trackers ("cars"), each with its own
 * name, phone number, and optional command PIN — and choose which one is
 * currently active via the radio button on each row.
 */
class TrackerProfilesActivity : LockProtectedActivity() {

    private lateinit var adapter: TrackerProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker_profiles)

        val listView = findViewById<ListView>(R.id.profilesListView)
        adapter = TrackerProfileAdapter(this, TrackerProfileStore.getAll(this)) { refreshList() }
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.profilesEmptyText)

        findViewById<Button>(R.id.addProfileButton).setOnClickListener { onAddProfileClicked() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.updateItems(TrackerProfileStore.getAll(this))
    }

    private fun onAddProfileClicked() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply { hint = getString(R.string.tracker_profile_name_hint) }
        val phoneInput = EditText(this).apply {
            hint = getString(R.string.tracker_profile_phone_hint)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val pinInput = EditText(this).apply { hint = getString(R.string.tracker_profile_pin_hint) }
        container.addView(nameInput)
        container.addView(phoneInput)
        container.addView(pinInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.tracker_profiles_add_button)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val pin = PersianDigits.toEnglish(pinInput.text.toString().trim())

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, getString(R.string.tracker_profile_error_missing_fields), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                TrackerProfileStore.add(this, name, phone, pin)
                refreshList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
