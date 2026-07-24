package ir.lbo.locationsms

/**
 * Persian and Arabic-Indic keyboards type ۰۱۲۳۴۵۶۷۸۹ / ٠١٢٣٤٥٦٧٨٩ instead of
 * plain ASCII digits. `String.toLongOrNull()` doesn't understand those, so
 * every numeric field or incoming SMS command body should be passed through
 * this before parsing.
 */
object PersianDigits {
    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"

    fun toEnglish(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val persianIndex = PERSIAN.indexOf(ch)
            if (persianIndex >= 0) {
                sb.append('0' + persianIndex)
                continue
            }
            val arabicIndex = ARABIC.indexOf(ch)
            if (arabicIndex >= 0) {
                sb.append('0' + arabicIndex)
                continue
            }
            sb.append(ch)
        }
        return sb.toString()
    }
}
