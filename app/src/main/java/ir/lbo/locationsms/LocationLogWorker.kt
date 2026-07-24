package ir.lbo.locationsms

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class LocationLogWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        // A fix older than this is treated as stale for geofence purposes
        // and skipped rather than acted on.
        private const val MAX_ACCEPTABLE_LOCATION_AGE_MS = 15 * 60 * 1000L // 15 minutes

        // A fix reporting worse accuracy than this (meters) is treated as
        // too coarse to trust for geofence purposes.
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 150f

        // Minimum hysteresis band applied around the geofence radius, even
        // when the fix reports better accuracy than this.
        private const val MIN_HYSTERESIS_METERS = 25f
    }

    override suspend fun doWork(): Result {
        // Cheap checks that don't need a GPS fix run first, so they still
        // happen even if the location fetch below fails or times out.
        checkSimChange(applicationContext)
        checkBatteryLevel(applicationContext)

        val location = LocationHelper.getCurrentLocation(applicationContext)
            ?: return Result.retry()

        checkGeofence(applicationContext, location)
        checkAdditionalGeofenceZones(applicationContext, location)
        maybeLogLocation(applicationContext, location)

        return Result.success()
    }

    private fun maybeLogLocation(context: Context, location: Location) {
        val settings = SettingsRepository(context)
        val minDistance = settings.getMinLogDistanceMeters()

        if (minDistance > 0) {
            val last = settings.getLastLoggedLocation()
            if (last != null) {
                val results = FloatArray(1)
                Location.distanceBetween(last.first, last.second, location.latitude, location.longitude, results)
                val distanceMoved = results[0]

                if (distanceMoved < minDistance) {
                    // Hasn't moved far enough yet — skip writing a new row.
                    return
                }

                // Moved beyond the minimum distance: optionally notify by SMS.
                if (settings.isMovementAlertEnabled()) {
                    val roundedDistance = distanceMoved.toInt()
                    broadcastToNumbers(
                        context,
                        settings.getPhoneNumbersList(),
                        AlertTag.wrap(context.getString(R.string.sms_alert_movement, roundedDistance))
                    )
                }
            }
        }

        LocationLogger.appendEntry(context, location)
        settings.saveLastLoggedLocation(location.latitude, location.longitude)
    }

    private fun checkSimChange(context: Context) {
        val changed = SimWatcher.checkAndUpdate(context)
        if (!changed) return

        val settings = SettingsRepository(context)
        broadcastToNumbers(
            context,
            settings.getAllowedNumbersList(),
            AlertTag.wrap(context.getString(R.string.sms_alert_sim_changed, SimWatcher.getCurrentFingerprint(context)))
        )
    }

    private fun checkGeofence(context: Context, location: Location) {
        val settings = SettingsRepository(context)
        if (!settings.isGeofenceEnabled()) return

        val center = settings.getGeofenceCenter() ?: return
        val radius = settings.getGeofenceRadiusMeters()

        // A stale cached fix (e.g. the lastLocation fallback in
        // LocationHelper, which can be minutes or hours old) can look
        // perfectly accurate on paper while actually reflecting where the
        // phone used to be, not where it is now. Skip this tick rather
        // than act on outdated data.
        val ageMillis = System.currentTimeMillis() - location.time
        if (ageMillis > MAX_ACCEPTABLE_LOCATION_AGE_MS) return

        // A coarse/network fallback fix can be off by hundreds of meters.
        // Acting on it can flag a false "left the area" transition even
        // though the phone hasn't actually moved. Skip this tick and wait
        // for a better fix next time instead of alerting on noise.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) return

        val results = FloatArray(1)
        Location.distanceBetween(center.first, center.second, location.latitude, location.longitude, results)
        val distance = results[0]

        val previousState = settings.getGeofenceState()

        // Hysteresis: require crossing the radius by a safety margin (scaled
        // to this fix's own reported accuracy, with a sane floor) before
        // flipping state. Without this, ordinary GPS jitter of a few tens of
        // meters near the boundary — completely normal even when the phone
        // hasn't moved at all — can flip the raw distance back and forth
        // across the exact radius value and trigger repeated false
        // exit/return alerts.
        val margin = if (location.hasAccuracy()) {
            location.accuracy.coerceAtLeast(MIN_HYSTERESIS_METERS)
        } else {
            MIN_HYSTERESIS_METERS
        }

        val newState = when {
            distance > radius + margin -> "outside"
            distance < radius - margin -> "inside"
            // Inside the ambiguous band right at the edge: don't change
            // anything yet, just wait for a clearer reading.
            else -> previousState ?: if (distance <= radius) "inside" else "outside"
        }

        settings.saveGeofenceState(newState)

        if (previousState == null || previousState == newState) return // no real transition yet

        val message = if (newState == "outside") {
            AlertTag.wrap(context.getString(R.string.sms_alert_geofence_exit))
        } else {
            AlertTag.wrap(context.getString(R.string.sms_alert_geofence_enter))
        }
        broadcastToNumbers(context, settings.getAllowedNumbersList(), message)
    }

    /**
     * Same logic as checkGeofence() above (stale/low-accuracy fixes are
     * skipped, hysteresis around the radius prevents GPS-jitter false
     * alerts), applied to every additional named zone instead of the one
     * default SMS-controlled geofence. Each zone alerts independently and
     * names itself in the message so multiple zones don't get confused
     * with each other.
     */
    private fun checkAdditionalGeofenceZones(context: Context, location: Location) {
        val zones = GeofenceZoneStore.getAll(context)
        if (zones.isEmpty()) return

        val ageMillis = System.currentTimeMillis() - location.time
        if (ageMillis > MAX_ACCEPTABLE_LOCATION_AGE_MS) return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) return

        val margin = if (location.hasAccuracy()) {
            location.accuracy.coerceAtLeast(MIN_HYSTERESIS_METERS)
        } else {
            MIN_HYSTERESIS_METERS
        }

        val settings = SettingsRepository(context)
        val results = FloatArray(1)

        zones.filter { it.enabled }.forEach { zone ->
            Location.distanceBetween(zone.latitude, zone.longitude, location.latitude, location.longitude, results)
            val distance = results[0]
            val previousState = zone.state

            val newState = when {
                distance > zone.radiusMeters + margin -> "outside"
                distance < zone.radiusMeters - margin -> "inside"
                else -> previousState ?: if (distance <= zone.radiusMeters) "inside" else "outside"
            }

            GeofenceZoneStore.updateState(context, zone.id, newState)

            if (previousState == null || previousState == newState) return@forEach

            val message = if (newState == "outside") {
                AlertTag.wrap(context.getString(R.string.sms_alert_zone_exit, zone.name))
            } else {
                AlertTag.wrap(context.getString(R.string.sms_alert_zone_enter, zone.name))
            }
            broadcastToNumbers(context, settings.getAllowedNumbersList(), message)
        }
    }

    private fun checkBatteryLevel(context: Context) {
        val settings = SettingsRepository(context)
        if (!settings.isBatteryAlertEnabled()) return

        val percent = BatteryHelper.getBatteryPercent(context)
        if (percent < 0) return // unknown right now, skip this tick

        val threshold = settings.getBatteryAlertThreshold()
        val newState = if (percent <= threshold) "low" else "normal"
        val previousState = settings.getBatteryAlertState()
        settings.saveBatteryAlertState(newState)

        if (previousState == null || previousState == newState) return // no real transition yet

        val message = if (newState == "low") {
            AlertTag.wrap(context.getString(R.string.sms_alert_battery_low, percent))
        } else {
            AlertTag.wrap(context.getString(R.string.sms_alert_battery_normal, percent))
        }
        broadcastToNumbers(context, settings.getAllowedNumbersList(), message)
    }

    private fun broadcastToNumbers(context: Context, numbers: List<String>, message: String) {
        numbers.forEach { phone ->
            val data = Data.Builder()
                .putString(ReplyTextWorker.KEY_PHONE, phone)
                .putString(ReplyTextWorker.KEY_TEXT, message)
                .build()

            val request = OneTimeWorkRequestBuilder<ReplyTextWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
