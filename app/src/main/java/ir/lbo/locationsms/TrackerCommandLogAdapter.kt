package ir.lbo.locationsms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackerCommandLogAdapter(context: Context, private var items: List<TrackerCommandLogEntry>) :
    ArrayAdapter<TrackerCommandLogEntry>(context, R.layout.item_command_log, ArrayList(items)) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun updateItems(newItems: List<TrackerCommandLogEntry>) {
        items = newItems
        clear()
        addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_command_log, parent, false)

        val entry = items[position]
        view.findViewById<TextView>(R.id.logTimestamp).text = dateFormat.format(Date(entry.timestamp))
        view.findViewById<TextView>(R.id.logSenderCommand).text = "${entry.sender} ← ${entry.command}"
        view.findViewById<TextView>(R.id.logResult).text = entry.result

        return view
    }
}
