package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

/**
 * The loop runs of the last hours, in numbers: what BG did and what AutoISF did. There is no meal
 * and no slot in this: it is simply the time before now.
 *
 * All glucose values are in mg/dl. "Above target" values are the difference to the target of
 * the runs (the profile target, or the temp target when one was set).
 */
data class AutoIsfRecentHours(
    /** First run of the hours that were looked at. */
    val start: Long,
    /** Last run of the hours that were looked at. */
    val end: Long,
    val targetBg: Double,
    val peakBg: Double,
    val peakTime: Long,
    /** Minutes with BG above target + [AutoIsfRecentAnalyzer.ABOVE_1_MGDL]. */
    val minutesAbove1: Int,
    /** Minutes with BG above target + [AutoIsfRecentAnalyzer.ABOVE_2_MGDL]. */
    val minutesAbove2: Int,
    /** Minutes with BG below [AutoIsfLogAnalyzer.LOW_MGDL]. */
    val minutesLow: Int,
    /** BG of the last run. */
    val endBg: Double,
    val smbCount: Int,
    val smbUnits: Double,
    val runs: Int,
    /** Runs where autoISF_max cut the factor, in % of [runs]. */
    val maxLimitedPct: Double,
    /** SMBs as big as maxBolus allowed, in % of [smbCount]. */
    val smbAtMaxBolusPct: Double,
    /** The factor that drove ISF most often, over the runs with a factor above 1. */
    val dominantDriver: AutoIsfDriver,
    /** A temp target was active in at least one run. Such hours are not rated. */
    val hadTempTarget: Boolean,
    /**
     * The runs do not cover the whole time that was looked at: the log began late or the loop
     * stopped. Shown, but not rated.
     */
    val incomplete: Boolean
) {

    val peakAboveTarget: Double get() = peakBg - targetBg
    val endAboveTarget: Double get() = endBg - targetBg
    val hadLow: Boolean get() = minutesLow > 0

    /** Counts as hours that can be rated: no temp target and the log covers them. */
    val used: Boolean get() = !hadTempTarget && !incomplete
}

/** Works out the numbers of the loop runs of the last hours. */
class AutoIsfRecentAnalyzer {

    private val analyzer = AutoIsfLogAnalyzer()

    /**
     * @param loopRuns runs of the period, one per BG reading
     * @param now the time of the analysis
     * @param windowMinutes how far back from [now] the runs are looked at
     * @return null when there are too few runs in that time (sensor gap, phone off)
     */
    fun look(loopRuns: List<AutoIsfLogRun>, now: Long, windowMinutes: Int): AutoIsfRecentHours? {
        val from = now - windowMinutes * MINUTE_MILLIS
        val runs = loopRuns.filter { it.timestamp in from..now }.sortedBy { it.timestamp }
        if (runs.size < MIN_RUNS) return null
        val tolerance = COVER_TOLERANCE_MINUTES * MINUTE_MILLIS
        val incomplete = runs.first().timestamp - from > tolerance || now - runs.last().timestamp > tolerance
        val target = median(runs.mapNotNull { it.targetBg }) ?: return null
        val peak = runs.maxBy { it.bg }
        val withDriver = runs.map { analyzer.driver(it) }.filter { it != AutoIsfDriver.None }
        val dominant = withDriver.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: AutoIsfDriver.None
        val smbs = runs.count { it.smbUnits > 0.0 }
        fun pct(n: Int, of: Int) = if (of == 0) 0.0 else 100.0 * n / of
        return AutoIsfRecentHours(
            start = runs.first().timestamp,
            end = runs.last().timestamp,
            targetBg = target,
            peakBg = peak.bg,
            peakTime = peak.timestamp,
            minutesAbove1 = runs.count { it.bg > target + ABOVE_1_MGDL } * RUN_MINUTES,
            minutesAbove2 = runs.count { it.bg > target + ABOVE_2_MGDL } * RUN_MINUTES,
            minutesLow = runs.count { it.bg < AutoIsfLogAnalyzer.LOW_MGDL } * RUN_MINUTES,
            endBg = runs.last().bg,
            smbCount = smbs,
            smbUnits = runs.sumOf { it.smbUnits },
            runs = runs.size,
            maxLimitedPct = pct(runs.count { it.limitedByAutoIsfMax }, runs.size),
            smbAtMaxBolusPct = pct(runs.count { it.smbAtMaxBolus }, smbs),
            dominantDriver = dominant,
            hadTempTarget = runs.any { it.tempTarget },
            incomplete = incomplete
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    companion object {

        /** Above target by this much counts as "above" (2 mmol/l). */
        const val ABOVE_1_MGDL = 36.0

        /** Above target by this much counts as "high" (4 mmol/l). */
        const val ABOVE_2_MGDL = 72.0

        /** Fewer runs than this is not enough to look at (sensor gap, phone off). */
        private const val MIN_RUNS = 6

        /** The runs must begin and end within this many minutes of the edges of the time looked at. */
        private const val COVER_TOLERANCE_MINUTES = 15

        private const val RUN_MINUTES = 5
        private const val MINUTE_MILLIS = 60_000L
    }
}
