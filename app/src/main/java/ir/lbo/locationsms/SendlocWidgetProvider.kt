package ir.lbo.locationsms

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A home-screen widget with a single button: "get live location". Tapping
 * it sends the sendloc SMS command straight to the saved tracker phone
 * number, without opening the app.
 */
class SendlocWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SEND_LOC = "ir.lbo.locationsms.action.WIDGET_SENDLOC"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SEND_LOC) {
            sendLocationRequest(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SendlocWidgetProvider::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }
    }

    private fun sendLocationRequest(context: Context) {
        val profile = TrackerProfileStore.getSelected(context)
        if (profile == null) {
            Toast.makeText(context, context.getString(R.string.widget_no_phone_saved), Toast.LENGTH_LONG).show()
            return
        }

        val command = if (!profile.pin.isNullOrBlank()) "${profile.pin} sendloc" else "sendloc"

        CommandSender.send(context, profile.phone, command)
        SettingsRepository(context).saveWidgetLastRequestTime(System.currentTimeMillis())
        Toast.makeText(context, context.getString(R.string.widget_request_sent, profile.name), Toast.LENGTH_SHORT).show()
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_sendloc)
        val settings = SettingsRepository(context)
        val lastTime = settings.getWidgetLastRequestTime()
        val profile = TrackerProfileStore.getSelected(context)

        views.setTextViewText(
            R.id.widgetStatusText,
            when {
                profile == null -> context.getString(R.string.widget_status_default)
                lastTime > 0 -> context.getString(
                    R.string.widget_last_request,
                    "${profile.name} — ${SimpleDateFormat("HH:mm", Locale.US).format(Date(lastTime))}"
                )
                else -> context.getString(R.string.widget_status_default_with_tracker, profile.name)
            }
        )

        val intent = Intent(context, SendlocWidgetProvider::class.java).apply {
            action = ACTION_SEND_LOC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetSendlocButton, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
