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
    val hadTempTarget: Boolean,
    /**
     * The runs do not cover the whole meal time plus tail: the period began or the log ended in
     * the middle of it (for example the meal is still going on). Shown, but not used for the
     * statistics.
     */
    val incomplete: Boolean
) {

    val peakAboveTarget: Double get() = peakBg - targetBg
    val endAboveTarget: Double get() = endBg - targetBg
    val hadLow: Boolean get() = minutesLow > 0

    /** Counts for the statistics. */
    val used: Boolean get() = !hadTempTarget && !incomplete
}

/** Statistics over the used meals of one slot. */
data class AutoIsfMealStats(
    /** Meals used. */
    val meals: Int,
    /** Meals left out: a temp target was active, or the meal is incomplete. */
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
        // Walk the runs in time order. Inside one meal the minutes from the start of the meal time
        // only grow; when they drop, the next meal (the next day) has begun. This needs no day
        // key, so a time zone or a DST change cannot join or split meals.
        val groups = mutableListOf<MutableList<Pair<Int, AutoIsfLogRun>>>()
        var lastFromStart = Int.MAX_VALUE
        for (run in runs.sortedBy { it.timestamp }) {
            val fromStart = (minuteOfDay(run.timestamp) - window.startMinute).mod(MINUTES_PER_DAY)
            if (fromStart >= length) {
                lastFromStart = Int.MAX_VALUE
                continue
            }
            if (fromStart < lastFromStart) groups.add(mutableListOf())
            groups.last().add(fromStart to run)
            lastFromStart = fromStart
        }
        return groups.mapNotNull { episode(it, length) }
    }

    private fun episode(runsFromStart: List<Pair<Int, AutoIsfLogRun>>, length: Int): AutoIsfMealEpisode? {
        if (runsFromStart.size < MIN_RUNS) return null
        val runs = runsFromStart.map { it.second }
        val incomplete = runsFromStart.first().first > COVER_TOLERANCE_MINUTES ||
            length - runsFromStart.last().first > COVER_TOLERANCE_MINUTES + RUN_MINUTES
        val target = median(runs.mapNotNull { it.targetBg }) ?: return null
        val mealStartRun = carbsStart(runs)
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
            hadTempTarget = runs.any { it.tempTarget },
            incomplete = incomplete
        )
    }

    /**
     * The run where carbs were entered: the first run whose carbs on board are above zero and
     * above the run before (more than [CARBS_STEP_G]). Carbs still on board from before the meal
     * time do not count. Null when no carbs were entered.
     */
    private fun carbsStart(runs: List<AutoIsfLogRun>): AutoIsfLogRun? {
        var previous = runs.first().cob ?: 0.0
        for (run in runs.drop(1)) {
            val cob = run.cob ?: 0.0
            if (cob > 0.0 && cob - previous > CARBS_STEP_G) return run
            previous = cob
        }
        return null
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
        val used = episodes.filter { it.used }
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

        /** A meal whose first or last run is further than this from the edge of its time is incomplete. */
        private const val COVER_TOLERANCE_MINUTES = 15

        /** Carbs on board must grow by more than this to count as a new meal entry. */
        private const val CARBS_STEP_G = 5.0

        private const val RUN_MINUTES = 5
        private const val RISE_MGDL = 18.0
        private const val RISE_MINUTES = 15L
        private const val MINUTES_PER_DAY = 24 * 60
        private const val MINUTE_MILLIS = 60_000L
    }
}
