package ir.lbo.locationsms

import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Shows every SMS command the Tracker has received and executed (or
 * rejected), newest first — useful for troubleshooting "why didn't my
 * command work?" without needing to look at logcat.
 */
class TrackerCommandHistoryActivity : LockProtectedActivity() {

    private lateinit var adapter: TrackerCommandLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker_command_history)

        val listView = findViewById<ListView>(R.id.commandLogListView)
        adapter = TrackerCommandLogAdapter(this, TrackerCommandLogStore.getAll(this))
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.emptyCommandLogText)

        findViewById<Button>(R.id.clearCommandLogButton).setOnClickListener {
            TrackerCommandLogStore.clear(this)
            adapter.updateItems(emptyList())
            Toast.makeText(this, getString(R.string.command_history_cleared_toast), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.updateItems(TrackerCommandLogStore.getAll(this))
    }
}
