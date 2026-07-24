package ir.lbo.locationsms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast

class TrackerProfileAdapter(
    context: Context,
    private var items: List<TrackerProfile>,
    private val onChanged: () -> Unit
) : ArrayAdapter<TrackerProfile>(context, R.layout.item_tracker_profile, ArrayList(items)) {

    fun updateItems(newItems: List<TrackerProfile>) {
        items = newItems
        clear()
        addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_tracker_profile, parent, false)
        val profile = items[position]
        val selectedId = TrackerProfileStore.getSelectedId(context)

        view.findViewById<TextView>(R.id.profileNameText).text = profile.name

        val radio = view.findViewById<RadioButton>(R.id.profileSelectedRadio)
        radio.setOnCheckedChangeListener(null)
        radio.isChecked = profile.id == selectedId
        radio.setOnClickListener {
            TrackerProfileStore.setSelectedId(context, profile.id)
            onChanged()
        }

        val nameInput = view.findViewById<EditText>(R.id.profileNameInput)
        val phoneInput = view.findViewById<EditText>(R.id.profilePhoneInput)
        val pinInput = view.findViewById<EditText>(R.id.profilePinInput)
        nameInput.setText(profile.name)
        phoneInput.setText(profile.phone)
        pinInput.setText(profile.pin ?: "")

        view.findViewById<Button>(R.id.profileSaveButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val pin = PersianDigits.toEnglish(pinInput.text.toString().trim())

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.tracker_profile_error_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            TrackerProfileStore.update(context, profile.id, name, phone, pin)
            Toast.makeText(context, context.getString(R.string.tracker_profile_saved_toast), Toast.LENGTH_SHORT).show()
            onChanged()
        }

        view.findViewById<Button>(R.id.profileDeleteButton).setOnClickListener {
            TrackerProfileStore.delete(context, profile.id)
            onChanged()
        }

        return view
    }
}
