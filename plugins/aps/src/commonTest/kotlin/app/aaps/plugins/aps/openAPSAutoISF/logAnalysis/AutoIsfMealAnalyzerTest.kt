package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.plugins.aps.openAPSAutoISF.MealWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoIsfMealAnalyzerTest {

    private val fiveMinutes = 5 * 60_000L
    private val dayMillis = 24 * 60 * 60_000L

    /** Timestamps are UTC, and the test clock is UTC too: minute of day comes straight from the timestamp. */
    private val analyzer = AutoIsfMealAnalyzer { ((it / 60_000L) % (24 * 60)).toInt() }

    private fun run(
        timestamp: Long,
        bg: Double,
        smb: Double = 0.0,
        cob: Double? = null,
        tempTarget: Boolean = false,
        limitedByMax: Boolean = false,
        acce: Double? = null
    ) = AutoIsfLogRun(
        timestamp = timestamp,
        bgTimestamp = timestamp,
        bg = bg,
        eventualBg = null,
        targetBg = 100.0,
        insulinReq = null,
        smbUnits = smb,
        iob = null,
        finalIsfFactor = null,
        acceIsf = acce,
        bgIsf = null,
        ppIsf = null,
        duraIsf = null,
        limitedByAutoIsfMax = limitedByMax,
        evenTargetSmb = false,
        smbRatioAtMax = false,
        smbStoppedByMinGuard = false,
        maxBolus = null,
        settings = null,
        cob = cob,
        tempTarget = tempTarget
    )

    /** A day of runs every 5 minutes at [bg], starting at midnight of [day]. */
    private fun flatDay(day: Int, bg: Double): MutableList<AutoIsfLogRun> =
        MutableList(288) { run(day * dayMillis + it * fiveMinutes, bg) }

    private val lunch = MealWindows.Window(11 * 60, 14 * 60)

    @Test
    fun `one meal per day inside meal time plus tail`() {
        val runs = flatDay(0, 100.0) + flatDay(1, 100.0)
        val meals = analyzer.episodes(runs, lunch, 180)

        assertEquals(2, meals.size)
        // 11:00 .. 16:55 = 6 hours = 72 runs
        assertEquals(72, meals[0].runs)
        assertEquals(11 * 60 * 60_000L, meals[0].start)
        assertEquals(16 * 60 * 60_000L + 55 * 60_000L, meals[0].end)
        assertEquals(dayMillis + 11 * 60 * 60_000L, meals[1].start)
    }

    @Test
    fun `meal over midnight stays one meal`() {
        val runs = flatDay(0, 100.0) + flatDay(1, 100.0)
        val meals = analyzer.episodes(runs, MealWindows.Window(22 * 60, 1 * 60), 60)

        // day 0: 22:00 .. 01:55 of day 1; day 1: 22:00 .. end of data
        assertEquals(2, meals.size)
        assertEquals(48, meals[0].runs)
        assertEquals(22 * 60 * 60_000L, meals[0].start)
        assertEquals(dayMillis + 115 * 60_000L, meals[0].end)
    }

    @Test
    fun `peak and times are measured from the carbs`() {
        val runs = flatDay(0, 100.0)
        val lunchStart = 11 * 60 * 60_000L
        // carbs at 12:00, rise to a peak of 190 at 13:00, back to 110 at 16:55, one low run at 15:00
        val carbs = lunchStart + 60 * 60_000L
        for (i in 0 until 72) {
            val t = lunchStart + i * fiveMinutes
            val index = (t / fiveMinutes).toInt()
            val bg = when {
                t < carbs                    -> 100.0
                t <= carbs + 60 * 60_000L    -> 100.0 + 90.0 * (t - carbs) / (60 * 60_000L)
                t == carbs + 180 * 60_000L   -> 65.0
                else                         -> 190.0 - 80.0 * (t - carbs - 60 * 60_000L) / (235 * 60_000L)
            }
            runs[index] = run(t, bg, smb = if (t == carbs + fiveMinutes) 1.5 else 0.0, cob = if (t >= carbs) 40.0 else 0.0, limitedByMax = t in carbs..(carbs + 30 * 60_000L), acce = 1.3)
        }
        val meal = analyzer.episodes(runs, lunch, 180).single()

        assertEquals(carbs, meal.mealStart)
        assertEquals(190.0, meal.peakBg)
        assertEquals(60, meal.minutesToPeak)
        assertEquals(90.0, meal.peakAboveTarget)
        assertTrue(meal.minutesAbove1 > 0)
        assertTrue(meal.minutesAbove2 > 0)
        assertTrue(meal.minutesAbove2 < meal.minutesAbove1)
        assertEquals(5, meal.minutesLow)
        assertTrue(meal.hadLow)
        assertEquals(1, meal.smbCount)
        assertEquals(1.5, meal.smbUnits)
        assertEquals(AutoIsfDriver.Acce, meal.dominantDriver)
        assertFalse(meal.hadTempTarget)
    }

    @Test
    fun `without carbs the first rise is the meal start`() {
        val runs = flatDay(0, 100.0)
        val riseAt = 12 * 60 * 60_000L
        for (i in 0 until 12) runs[((riseAt + i * fiveMinutes) / fiveMinutes).toInt()] = run(riseAt + i * fiveMinutes, 100.0 + 10.0 * i)
        val meal = analyzer.episodes(runs, lunch, 180).single()

        // 12:00 is 100, 12:10 is 120: the rise of 18 mg/dl within 15 minutes starts at 12:00
        assertEquals(riseAt, meal.mealStart)
    }

    @Test
    fun `temp target meals are left out of the statistics`() {
        val runs = flatDay(0, 100.0) + flatDay(1, 100.0) + flatDay(2, 100.0)
        val lunchDay1 = dayMillis + 12 * 60 * 60_000L
        val changed = runs.map { if (it.timestamp == lunchDay1) run(it.timestamp, 100.0, tempTarget = true) else it }
        val meals = analyzer.episodes(changed, lunch, 180)
        val stats = analyzer.stats(meals)

        assertEquals(3, meals.size)
        assertEquals(2, stats.meals)
        assertEquals(1, stats.excluded)
        assertEquals(0.0, stats.meanPeakAboveTarget)
        assertEquals(0, stats.mealsWithLow)
    }

    @Test
    fun `short gaps are not meals`() {
        val runs = listOf(run(11 * 60 * 60_000L, 100.0), run(11 * 60 * 60_000L + fiveMinutes, 100.0))
        assertTrue(analyzer.episodes(runs, lunch, 180).isEmpty())
    }
}
