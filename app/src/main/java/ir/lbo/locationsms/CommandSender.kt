package ir.lbo.locationsms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast

/**
 * Sends a command SMS from the Viewer and reports back if the *send itself*
 * failed (no signal, radio off, etc.) — SMS is normally "fire and forget",
 * so without this, a command that never left the phone looks identical to
 * one that was sent but never answered.
 */
object CommandSender {

    private const val ACTION_SMS_SENT = "ir.lbo.locationsms.action.SMS_SENT"
    private const val EXTRA_COMMAND = "command"

    @Volatile
    private var receiverRegistered = false

    fun send(context: Context, phone: String, command: String) {
        val appContext = context.applicationContext

        val smsManager = if (Build.VERSION.SDK_INT >= 31) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return

        ensureStatusReceiverRegistered(appContext)

        val parts = smsManager.divideMessage(command)
        val baseRequestCode = System.currentTimeMillis().toInt()
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val sentPendingIntents = ArrayList<PendingIntent>(parts.size)
        for (i in parts.indices) {
            val sentIntent = Intent(ACTION_SMS_SENT).apply {
                setPackage(appContext.packageName)
                putExtra(EXTRA_COMMAND, command)
            }
            sentPendingIntents.add(
                PendingIntent.getBroadcast(appContext, baseRequestCode + i, sentIntent, piFlags)
            )
        }

        smsManager.sendMultipartTextMessage(phone, null, parts, sentPendingIntents, null)
    }

    /**
     * Registers one long-lived, app-scoped receiver (guarded so it only
     * happens once per process) that listens for the SMS subsystem's result
     * broadcast for every command this object sends, and surfaces a toast
     * only when the send itself actually failed.
     */
    private fun ensureStatusReceiverRegistered(context: Context) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action != ACTION_SMS_SENT) return
                    if (resultCode == Activity.RESULT_OK) return // delivered to the radio successfully

                    val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
                    Toast.makeText(
                        receiverContext,
                        receiverContext.getString(R.string.command_send_failed_toast, command),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val filter = IntentFilter(ACTION_SMS_SENT)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }

            receiverRegistered = true
        }
    }
}
