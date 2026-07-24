package ir.lbo.locationsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class SmsReceiver : BroadcastReceiver() {

    private val autosendOnPattern = Regex("(?i)^autosend\\s+(\\d+)\\s+on$")
    private val moveAlertOnPattern = Regex("(?i)^MoveAlert\\s+(\\d+)\\s+on$")
    private val geofenceOnPattern = Regex("(?i)^Geofence\\s+(\\d+)\\s+on$")
    private val lowBatteryOnPattern = Regex("(?i)^LowbatteryAlert\\s+(\\d+)\\s+on$")
    private val setAdminNumbersPattern = Regex("(?i)^SetAdminNumbers\\s+(.+)$")
    private val setAutosendNumbersPattern = Regex("(?i)^SetAutosendNumbers\\s+(.+)$")
    private val setLogTimerPattern = Regex("(?i)^SetLogTimer\\s+(\\d+)$")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val rawBody = messages.joinToString(separator = "") { it.messageBody ?: "" }.trim()
        // Normalize Persian/Arabic-Indic digits (۰۱۲... / ٠١٢...) to ASCII so
        // the regexes below and toLongOrNull() both work regardless of which
        // keyboard the sender used.
        val normalizedBody = PersianDigits.toEnglish(rawBody)

        val settings = SettingsRepository(context)
        val allowedNumbers = settings.getAllowedNumbersList()

        // All remote commands require the sender to be in the whitelist.
        // If the list is empty, nothing is trusted yet.
        if (!PhoneUtils.isAllowed(sender, allowedNumbers)) return

        // Optional shared PIN: if configured, the command must start with
        // "<pin> " or it's silently ignored (no reply, so an attacker
        // guessing at commands doesn't learn whether a PIN is set at all).
        val pin = settings.getCommandPin()
        val fullBody = if (!pin.isNullOrBlank()) {
            val prefix = "$pin "
            if (!normalizedBody.startsWith(prefix, ignoreCase = true)) return
            normalizedBody.substring(prefix.length).trim()
        } else {
            normalizedBody
        }

        val autosendOnMatch = autosendOnPattern.find(fullBody)
        val moveAlertOnMatch = moveAlertOnPattern.find(fullBody)
        val geofenceOnMatch = geofenceOnPattern.find(fullBody)
        val lowBatteryOnMatch = lowBatteryOnPattern.find(fullBody)
        val setAdminNumbersMatch = setAdminNumbersPattern.find(fullBody)
        val setAutosendNumbersMatch = setAutosendNumbersPattern.find(fullBody)
        val setLogTimerMatch = setLogTimerPattern.find(fullBody)

        when {
            fullBody.equals("sendloc", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_getting_location))
                enqueueLocationReply(context, sender)
            }
            autosendOnMatch != null -> {
                val minutes = autosendOnMatch.groupValues[1].toLongOrNull() ?: 15L
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleAutoSendOn(context, settings, sender, minutes)
            }
            fullBody.equals("autosend off", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleAutoSendOff(context, settings, sender)
            }
            fullBody.equals("sendlog", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_sending_email))
                enqueueSendLogEmail(context, sender)
            }
            fullBody.equals("dellog", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleDeleteLogs(context, sender)
            }
            fullBody.equals("ping", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handlePing(context, sender)
            }
            moveAlertOnMatch != null -> {
                val distance = moveAlertOnMatch.groupValues[1].toLongOrNull() ?: 50L
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleMoveAlertOn(context, settings, sender, distance)
            }
            fullBody.equals("MoveAlert off", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleMoveAlertOff(context, settings, sender)
            }
            geofenceOnMatch != null -> {
                val radius = geofenceOnMatch.groupValues[1].toLongOrNull() ?: 500L
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleGeofenceOn(context, settings, sender, radius)
            }
            fullBody.equals("Geofence off", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleGeofenceOff(context, settings, sender)
            }
            lowBatteryOnMatch != null -> {
                val percent = lowBatteryOnMatch.groupValues[1].toLongOrNull() ?: 15L
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleLowBatteryOn(context, settings, sender, percent)
            }
            fullBody.equals("LowbatteryAlert off", ignoreCase = true) -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleLowBatteryOff(context, settings, sender)
            }
            setAdminNumbersMatch != null -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleSetAdminNumbers(context, settings, sender, setAdminNumbersMatch.groupValues[1].trim())
            }
            setAutosendNumbersMatch != null -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleSetAutosendNumbers(context, settings, sender, setAutosendNumbersMatch.groupValues[1].trim())
            }
            setLogTimerMatch != null -> {
                val minutes = setLogTimerMatch.groupValues[1].toLongOrNull() ?: 15L
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_executed))
                handleSetLogTimer(context, settings, sender, minutes)
            }
            else -> {
                logCommand(context, sender, fullBody, context.getString(R.string.sms_log_unknown_command))
            }
        }
    }

    private fun logCommand(context: Context, sender: String, command: String, result: String) {
        TrackerCommandLogStore.addEntry(context, sender, command, result)
    }

    private fun enqueueLocationReply(context: Context, sender: String) {
        val data = Data.Builder()
            .putString(ReplyLocationWorker.KEY_PHONE, sender)
            .build()

        val request = OneTimeWorkRequestBuilder<ReplyLocationWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun handleAutoSendOn(
        context: Context,
        settings: SettingsRepository,
        sender: String,
        requestedMinutes: Long
    ) {
        val interval = if (requestedMinutes < 15) 15 else requestedMinutes
        settings.saveIntervalMinutes(interval)
        settings.saveAutoSendEnabled(true)
        WorkScheduler.schedule(context, interval)

        replyText(context, sender, context.getString(R.string.sms_reply_autosend_on, interval))
    }

    private fun handleAutoSendOff(context: Context, settings: SettingsRepository, sender: String) {
        settings.saveAutoSendEnabled(false)
        WorkScheduler.cancel(context)

        replyText(context, sender, context.getString(R.string.sms_reply_autosend_off))
    }

    private fun enqueueSendLogEmail(context: Context, sender: String) {
        val data = Data.Builder()
            .putString(SendLogEmailWorker.KEY_REPLY_PHONE, sender)
            .build()

        val request = OneTimeWorkRequestBuilder<SendLogEmailWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun handleDeleteLogs(context: Context, sender: String) {
        val deletedCount = LocationLogger.deleteArchivedLogFiles(context)
        replyText(context, sender, context.getString(R.string.sms_reply_dellog_done, deletedCount))
    }

    private fun handlePing(context: Context, sender: String) {
        val battery = BatteryHelper.getBatteryPercent(context)
        val batteryText = if (battery in 0..100) context.getString(R.string.sms_percent_value, battery) else context.getString(R.string.sms_reply_ping_battery_unknown)
        replyText(context, sender, context.getString(R.string.sms_reply_ping, batteryText))
    }

    private fun handleMoveAlertOn(context: Context, settings: SettingsRepository, sender: String, requestedDistance: Long) {
        val distance = if (requestedDistance < 0) 0 else requestedDistance
        settings.saveMinLogDistanceMeters(distance)
        settings.saveMovementAlertEnabled(true)
        replyText(context, sender, context.getString(R.string.sms_reply_movealert_on, distance))
    }

    private fun handleMoveAlertOff(context: Context, settings: SettingsRepository, sender: String) {
        settings.saveMovementAlertEnabled(false)
        replyText(context, sender, context.getString(R.string.sms_reply_movealert_off))
    }

    private fun handleGeofenceOn(context: Context, settings: SettingsRepository, sender: String, requestedRadius: Long) {
        val radius = if (requestedRadius < 50) 50 else requestedRadius
        settings.saveGeofenceRadiusMeters(radius)

        val data = Data.Builder()
            .putString(GeofenceEnableWorker.KEY_PHONE, sender)
            .build()

        val request = OneTimeWorkRequestBuilder<GeofenceEnableWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun handleGeofenceOff(context: Context, settings: SettingsRepository, sender: String) {
        settings.saveGeofenceEnabled(false)
        replyText(context, sender, context.getString(R.string.sms_reply_geofence_off))
    }

    private fun handleLowBatteryOn(context: Context, settings: SettingsRepository, sender: String, requestedPercent: Long) {
        val percent = requestedPercent.coerceIn(1L, 90L)
        settings.saveBatteryAlertThreshold(percent)
        settings.saveBatteryAlertEnabled(true)
        replyText(context, sender, context.getString(R.string.sms_reply_lowbattery_on, percent))
    }

    private fun handleLowBatteryOff(context: Context, settings: SettingsRepository, sender: String) {
        settings.saveBatteryAlertEnabled(false)
        replyText(context, sender, context.getString(R.string.sms_reply_lowbattery_off))
    }

    private fun handleSetAdminNumbers(context: Context, settings: SettingsRepository, sender: String, numbersRaw: String) {
        settings.saveAllowedNumbersRaw(numbersRaw)
        val count = settings.getAllowedNumbersList().size
        replyText(context, sender, context.getString(R.string.sms_reply_admin_numbers_updated, count))
    }

    private fun handleSetAutosendNumbers(context: Context, settings: SettingsRepository, sender: String, numbersRaw: String) {
        settings.savePhoneNumbersRaw(numbersRaw)
        val count = settings.getPhoneNumbersList().size
        replyText(context, sender, context.getString(R.string.sms_reply_autosend_numbers_updated, count))
    }

    private fun handleSetLogTimer(context: Context, settings: SettingsRepository, sender: String, requestedMinutes: Long) {
        val minutes = if (requestedMinutes < 15) 15 else requestedMinutes
        settings.saveLogIntervalMinutes(minutes)
        LogWorkScheduler.schedule(context, minutes)
        replyText(context, sender, context.getString(R.string.sms_reply_log_timer_updated, minutes))
    }

    private fun replyText(context: Context, phone: String, text: String) {
        val data = Data.Builder()
            .putString(ReplyTextWorker.KEY_PHONE, phone)
            .putString(ReplyTextWorker.KEY_TEXT, text)
            .build()

        val request = OneTimeWorkRequestBuilder<ReplyTextWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
