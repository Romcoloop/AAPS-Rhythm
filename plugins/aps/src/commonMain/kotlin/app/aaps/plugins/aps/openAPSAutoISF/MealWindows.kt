package app.aaps.plugins.aps.openAPSAutoISF

/**
 * The time windows of the AutoISF slots (breakfast, lunch, dinner, night).
 *
 * A window is written as "HH:MM-HH:MM", for example "06:30-09:00".
 * An empty or wrong text means the window is not used.
 * A window can go over midnight, for example "22:00-01:00".
 */
object MealWindows {

    data class Window(val startMinute: Int, val endMinute: Int) {

        fun contains(minuteOfDay: Int): Boolean =
            if (startMinute < endMinute) minuteOfDay in startMinute until endMinute
            else minuteOfDay >= startMinute || minuteOfDay < endMinute   // over midnight
    }

    private val pattern = Regex("""^\s*(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})\s*$""")

    /** Returns null when the text is empty or not valid. */
    fun parse(text: String): Window? {
        val m = pattern.matchEntire(text) ?: return null
        val (h1, m1, h2, m2) = m.destructured.toList().map { it.toInt() }
        if (h1 > 23 || h2 > 23 || m1 > 59 || m2 > 59) return null
        val start = h1 * 60 + m1
        val end = h2 * 60 + m2
        if (start == end) return null
        return Window(start, end)
    }
}
