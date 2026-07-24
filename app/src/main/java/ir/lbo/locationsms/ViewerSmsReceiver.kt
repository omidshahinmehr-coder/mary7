package ir.lbo.locationsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.regex.Pattern

class ViewerSmsReceiver : BroadcastReceiver() {

    // Matches "lat, lng" pairs like the ones the tracker app sends,
    // e.g. "Location: 35.715298, 51.404343"
    private val locationPattern: Pattern =
        Pattern.compile("([-+]?\\d{1,3}\\.\\d+),\\s*([-+]?\\d{1,3}\\.\\d+)")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val rawBody = messages.joinToString(separator = "") { it.messageBody ?: "" }.trim()

        TrackerProfileStore.migrateFromLegacySettingsIfNeeded(context)

        val matchedTracker = TrackerProfileStore.getAll(context)
            .find { PhoneUtils.isAllowed(sender, listOf(it.phone)) }

        // Only record messages coming from one of the configured tracker phones.
        if (matchedTracker == null) return

        val isAlert = AlertTag.isAlert(rawBody)
        val fullBody = if (isAlert) AlertTag.strip(rawBody) else rawBody

        var latitude: Double? = null
        var longitude: Double? = null
        val matcher = locationPattern.matcher(fullBody)
        if (matcher.find()) {
            latitude = matcher.group(1)?.toDoubleOrNull()
            longitude = matcher.group(2)?.toDoubleOrNull()
        }

        ViewerHistoryStore.addEntry(
            context,
            ViewerHistoryEntry(
                timestamp = System.currentTimeMillis(),
                rawText = fullBody,
                latitude = latitude,
                longitude = longitude,
                isAlert = isAlert,
                trackerId = matchedTracker.id
            )
        )

        if (isAlert) {
            NotificationHelper.showAlert(context, fullBody)
        }

        NewMessageNotifier.notifyListeners()
    }
}
