package pt.aguiarvieira.xmuks.core.designsystem.util

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Room-list timestamps: the time today, the weekday within the last week, day and month this year,
 * a short date otherwise — all in the user's locale and 12/24 h preference.
 */
object ListTimestamps {
    fun format(
        timestamp: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        is24Hour: Boolean = true,
    ): String {
        if (timestamp <= 0) return ""
        val then = Instant.ofEpochMilli(timestamp).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        val pattern =
            when {
                days <= 0L -> if (is24Hour) "HH:mm" else "h:mm a"
                days < WEEK_DAYS -> "EEE"
                then.year == today.year -> DateFormat.getBestDateTimePattern(locale, "dMMM")
                else -> null
            }
        val formatter =
            pattern?.let { DateTimeFormatter.ofPattern(it, locale) }
                ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        return then.format(formatter)
    }

    private const val WEEK_DAYS = 7L
}
