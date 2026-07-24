package ir.lbo.locationsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * WorkManager's own periodic jobs generally survive a reboot on their own,
 * but re-scheduling explicitly here is cheap insurance, and gives us one
 * obvious place to skip devices that were never configured as a Tracker
 * (a Viewer install shouldn't start GPS/location-log work on every boot).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = SettingsRepository(context)

        // Heuristic: this device has been through the Tracker settings screen
        // at least once (it has allowed numbers configured) and isn't set up
        // as a Viewer (no tracker-to-control phone number saved).
        val looksLikeTracker =
            settings.getAllowedNumbersList().isNotEmpty() && settings.getTrackerViewerPhone().isNullOrBlank()

        if (!looksLikeTracker) return

        LogWorkScheduler.schedule(context, settings.getLogIntervalMinutes())

        if (settings.isAutoSendEnabled()) {
            WorkScheduler.schedule(context, settings.getIntervalMinutes())
        }
    }
}
