package app.aaps.plugins.aps.openAPSAutoISF

/**
 * Usual meal times (breakfast, lunch, dinner) for AutoISF.
 *
 * Inside a meal window the factors that add insulin after a meal
 * (bgBlend, pp_ISF and acce_ISF above target) work with full weight.
 * Outside all windows their weights are multiplied by the "outside meal" factor.
 *
 * A window is written as "HH:MM-HH:MM", for example "06:30-09:00".
 * An empty or wrong text means the window is not used.
 * A window can go over midnight, for example "22:00-01:00".
 *
 * If no window is defined at all, nothing changes (scale is always 1.0).
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

    data class Result(
        /** Multiplier for the meal related weights (1.0 = no change). */
        val scale: Double,
        /** True when now is inside one of the windows. */
        val inWindow: Boolean,
        /** Number of valid windows. */
        val windowCount: Int
    )

    /**
     * @param windowTexts texts of all windows
     * @param minuteOfDay minutes since local midnight (0..1439)
     * @param outsideFactor multiplier outside the windows, limited to 0.0..1.0
     */
    fun calculate(windowTexts: List<String>, minuteOfDay: Int, outsideFactor: Double): Result {
        val windows = windowTexts.mapNotNull { parse(it) }
        if (windows.isEmpty()) return Result(1.0, false, 0)
        val inWindow = windows.any { it.contains(minuteOfDay) }
        val scale = if (inWindow) 1.0 else outsideFactor.coerceIn(0.0, 1.0)
        return Result(scale, inWindow, windows.size)
    }
}
