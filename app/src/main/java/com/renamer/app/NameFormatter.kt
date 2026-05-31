package com.renamer.app

import java.util.Calendar
import java.util.Locale

/**
 * Turns a user supplied template such as `Holiday Pics {DD} {MM} {YYYY}` into a
 * concrete file name based on a photo's capture date.
 *
 * Supported tokens (case sensitive):
 *   {YYYY} four digit year      {YY} two digit year
 *   {MM}   two digit month      {MMM} short month name (Jan, Feb ...)
 *   {DD}   two digit day        {HH} two digit hour (24h)
 *   {mm}   two digit minute     {ss} two digit second
 */
object NameFormatter {

    private val ILLEGAL = Regex("""[\\/:*?"<>|]""")

    /** Builds the name body (without extension) for the given timestamp. */
    fun format(template: String, dateMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val monthName = cal.getDisplayName(
            Calendar.MONTH, Calendar.SHORT, Locale.getDefault()
        ) ?: month.toString()

        var out = template
        out = out.replace("{YYYY}", year.toString())
        out = out.replace("{YY}", (year % 100).pad())
        out = out.replace("{MMM}", monthName)
        out = out.replace("{MM}", month.pad())
        out = out.replace("{DD}", day.pad())
        out = out.replace("{HH}", hour.pad())
        out = out.replace("{mm}", minute.pad())
        out = out.replace("{ss}", second.pad())

        return sanitize(out)
    }

    /** Strips characters that are not allowed in file names. */
    fun sanitize(name: String): String {
        val cleaned = ILLEGAL.replace(name, "").trim().trimEnd('.')
        return cleaned.ifBlank { "renamed" }
    }

    /** Returns the extension of a display name, or empty string. */
    fun extensionOf(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        return if (dot in 1 until displayName.length - 1) {
            displayName.substring(dot + 1)
        } else {
            ""
        }
    }

    private fun Int.pad(): String = toString().padStart(2, '0')
}
