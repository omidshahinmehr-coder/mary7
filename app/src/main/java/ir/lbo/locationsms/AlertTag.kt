package ir.lbo.locationsms

/**
 * The Tracker prefixes urgent alert messages (movement, geofence, low
 * battery, SIM change) with this marker before sending them by SMS. The
 * Viewer strips it before showing the text and uses its presence to decide
 * whether to raise a real Android notification instead of a silent history
 * update.
 */
object AlertTag {
    private const val PREFIX = "[ALERT] "

    fun wrap(message: String): String = PREFIX + message

    fun strip(message: String): String =
        if (message.startsWith(PREFIX)) message.removePrefix(PREFIX) else message

    fun isAlert(message: String): Boolean = message.startsWith(PREFIX)
}
