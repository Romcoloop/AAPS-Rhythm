package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.plugins.aps.openAPSAutoISF.MealWindows

/**
 * One meal: the loop runs of one day inside a meal time plus the tail after it.
 *
 * All glucose values are in mg/dl. "Above target" values are the difference to the target of
 * the runs (the profile target, or the temp target when one was set).
 */
data class AutoIsfMealEpisode(
    /** First run of the meal time. */
    val start: Long,
    /** Last run inside the meal time plus tail. */
    val end: Long,
    /**
     * Where the meal is taken to begin: the first run with carbs on board, else the first run
     * where BG started to rise, else [start].
     */
    val mealStart: Long,
    val targetBg: Double,
    val bgAtMealStart: Double,
    val peakBg: Double,
    val peakTime: Long,
    /** Minutes from [mealStart] to [peakTime]. */
    val minutesToPeak: Int,
    /** Minutes with BG above target + [AutoIsfMealAnalyzer.ABOVE_1_MGDL]. */
    val minutesAbove1: Int,
    /** Minutes with BG above target + [AutoIsfMealAnalyzer.ABOVE_2_MGDL]. */
    val minutesAbove2: Int,
    /** Minutes with BG below [AutoIsfLogAnalyzer.LOW_MGDL]. */
    val minutesLow: Int,
    /** BG of the last run of the episode. */
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
    /** A temp target was active in at least one run. Such a meal is not used for the statistics. */
    val hadTempTarget: Boolean
) {

    val peakAboveTarget: Double get() = peakBg - targetBg
    val endAboveTarget: Double get() = endBg - targetBg
    val hadLow: Boolean get() = minutesLow > 0
}

/** Statistics over the used meals of one slot. */
data class AutoIsfMealStats(
    /** Meals used. */
    val meals: Int,
    /** Meals left out because a temp target was active. */
    val excluded: Int,
    val meanPeakAboveTarget: Double,
    val medianMinutesToPeak: Int,
    val meanMinutesAbove1: Double,
    val meanMinutesAbove2: Double,
    val mealsWithLow: Int,
    val meanEndAboveTarget: Double,
    val meanSmbUnits: Double,
    val maxLimitedPct: Double,
    val smbAtMaxBolusPct: Double,
    val dominantDriver: AutoIsfDriver
)

/**
 * Splits the loop runs of a meal slot into meals (one per day) and works out what happened after
 * each meal. The slot is chosen by the clock, the same way the algorithm chooses it, so a meal
 * here is simply "the meal time of that day plus the tail".
 */
class AutoIsfMealAnalyzer(
    /** Minutes since local midnight of a timestamp. */
    private val minuteOfDay: (Long) -> Int
) {

    private val analyzer = AutoIsfLogAnalyzer()

    /**
     * @param runs runs of the period, one per BG reading, oldest first
     * @param window the meal time of the slot
     * @param tailMinutes how long after the meal time the meal is still followed
     */
    fun episodes(runs: List<AutoIsfLogRun>, window: MealWindows.Window, tailMinutes: Int): List<AutoIsfMealEpisode> {
        val length = ((window.endMinute - window.startMinute).mod(MINUTES_PER_DAY)) + tailMinutes
        // Group by meal: minutes from the start of the meal time, then the day that start falls on.
        val groups = linkedMapOf<Long, MutableList<AutoIsfLogRun>>()
        for (run in runs) {
            val fromStart = (minuteOfDay(run.timestamp) - window.startMinute).mod(MINUTES_PER_DAY)
            if (fromStart >= length) continue
            val mealKey = (run.timestamp - fromStart * MINUTE_MILLIS) / (MINUTES_PER_DAY * MINUTE_MILLIS)
            groups.getOrPut(mealKey) { mutableListOf() }.add(run)
        }
        return groups.values.mapNotNull { episode(it) }
    }

    private fun episode(runs: List<AutoIsfLogRun>): AutoIsfMealEpisode? {
        if (runs.size < MIN_RUNS) return null
        val target = median(runs.mapNotNull { it.targetBg }) ?: return null
        val mealStartRun = runs.firstOrNull { (it.cob ?: 0.0) > 0.0 }
            ?: firstRise(runs)
            ?: runs.first()
        val afterStart = runs.filter { it.timestamp >= mealStartRun.timestamp }.ifEmpty { runs }
        val peak = afterStart.maxBy { it.bg }
        val withDriver = runs.map { analyzer.driver(it) }.filter { it != AutoIsfDriver.None }
        val dominant = withDriver.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: AutoIsfDriver.None
        val smbs = runs.count { it.smbUnits > 0.0 }
        fun pct(n: Int, of: Int) = if (of == 0) 0.0 else 100.0 * n / of
        return AutoIsfMealEpisode(
            start = runs.first().timestamp,
            end = runs.last().timestamp,
            mealStart = mealStartRun.timestamp,
            targetBg = target,
            bgAtMealStart = mealStartRun.bg,
            peakBg = peak.bg,
            peakTime = peak.timestamp,
            minutesToPeak = ((peak.timestamp - mealStartRun.timestamp) / MINUTE_MILLIS).toInt(),
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
            hadTempTarget = runs.any { it.tempTarget }
        )
    }

    /**
     * The run where BG started to rise: the last run before a rise of [RISE_MGDL] or more within
     * [RISE_MINUTES]. Null when BG never rose that fast.
     */
    private fun firstRise(runs: List<AutoIsfLogRun>): AutoIsfLogRun? {
        for (i in 1 until runs.size) {
            val run = runs[i]
            val from = runs.subList(0, i).lastOrNull {
                run.timestamp - it.timestamp <= RISE_MINUTES * MINUTE_MILLIS && run.bg - it.bg >= RISE_MGDL
            }
            if (from != null) return from
        }
        return null
    }

    fun stats(episodes: List<AutoIsfMealEpisode>): AutoIsfMealStats {
        val used = episodes.filter { !it.hadTempTarget }
        val n = used.size
        fun mean(values: List<Double>) = if (values.isEmpty()) 0.0 else values.sum() / values.size
        val drivers = used.map { it.dominantDriver }.filter { it != AutoIsfDriver.None }
        return AutoIsfMealStats(
            meals = n,
            excluded = episodes.size - n,
            meanPeakAboveTarget = mean(used.map { it.peakAboveTarget }),
            medianMinutesToPeak = median(used.map { it.minutesToPeak.toDouble() })?.toInt() ?: 0,
            meanMinutesAbove1 = mean(used.map { it.minutesAbove1.toDouble() }),
            meanMinutesAbove2 = mean(used.map { it.minutesAbove2.toDouble() }),
            mealsWithLow = used.count { it.hadLow },
            meanEndAboveTarget = mean(used.map { it.endAboveTarget }),
            meanSmbUnits = mean(used.map { it.smbUnits }),
            maxLimitedPct = mean(used.map { it.maxLimitedPct }),
            smbAtMaxBolusPct = mean(used.filter { it.smbCount > 0 }.map { it.smbAtMaxBolusPct }),
            dominantDriver = drivers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: AutoIsfDriver.None
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

        /** How long a meal is followed after the end of its meal time. */
        const val DEFAULT_TAIL_MINUTES = 180

        /** Fewer runs than this is not a meal (sensor gap, phone off). */
        private const val MIN_RUNS = 6

        private const val RUN_MINUTES = 5
        private const val RISE_MGDL = 18.0
        private const val RISE_MINUTES = 15L
        private const val MINUTES_PER_DAY = 24 * 60
        private const val MINUTE_MILLIS = 60_000L
    }
}
