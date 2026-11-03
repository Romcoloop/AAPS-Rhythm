package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoIsfRecentAnalyzerTest {

    private val minute = 60_000L
    private val fiveMinutes = 5 * minute
    private val analyzer = AutoIsfRecentAnalyzer()

    /** Any real time. The analysis looks at the hours before it. */
    private val now = 10 * 24 * 60 * minute
    private val from = now - 180 * minute

    private fun run(
        timestamp: Long,
        bg: Double,
        smb: Double = 0.0,
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
        cob = null,
        tempTarget = tempTarget
    )

    /** The 3 hours before [now], a run every 5 minutes at [bg]: 37 runs. */
    private fun series(bg: Double = 100.0): MutableList<AutoIsfLogRun> =
        MutableList(37) { run(from + it * fiveMinutes, bg) }

    @Test
    fun `flat hours have no minutes above target and no low`() {
        val hours = analyzer.look(series(), now, 180)!!

        assertEquals(37, hours.runs)
        assertEquals(from, hours.start)
        assertEquals(now, hours.end)
        assertEquals(0, hours.minutesAbove1)
        assertFalse(hours.hadLow)
        assertFalse(hours.incomplete)
        assertTrue(hours.used)
    }

    @Test
    fun `peak, minutes above target, low and SMBs are counted over all the hours`() {
        val runs = series()
        // rise to a peak of 190 after 60 minutes, back to 100, one low run, a correction SMB at the start
        for (i in 0 until 37) {
            val t = from + i * fiveMinutes
            val bg = when {
                i <= 12  -> 100.0 + 90.0 * i / 12
                i == 30  -> 65.0
                else     -> 190.0 - 90.0 * (i - 12) / 24
            }
            val smb = when (i) {
                0    -> 0.5
                13   -> 1.5
                else -> 0.0
            }
            runs[i] = run(t, bg, smb = smb, limitedByMax = i in 12..18, acce = 1.3)
        }
        val hours = analyzer.look(runs, now, 180)!!

        assertEquals(190.0, hours.peakBg)
        assertEquals(from + 12 * fiveMinutes, hours.peakTime)
        assertEquals(90.0, hours.peakAboveTarget)
        assertTrue(hours.minutesAbove1 > 0)
        assertTrue(hours.minutesAbove2 > 0)
        assertTrue(hours.minutesAbove2 < hours.minutesAbove1)
        assertEquals(5, hours.minutesLow)
        assertTrue(hours.hadLow)
        assertEquals(2, hours.smbCount)
        assertEquals(2.0, hours.smbUnits)
        assertEquals(AutoIsfDriver.Acce, hours.dominantDriver)
        assertTrue(hours.maxLimitedPct > 0.0)
        assertFalse(hours.hadTempTarget)
    }

    @Test
    fun `the hours can be 6 as well`() {
        val six = now - 360 * minute
        val runs = List(73) { run(six + it * fiveMinutes, 100.0) }

        assertEquals(73, analyzer.look(runs, now, 360)!!.runs)
        // the same runs seen as 3 hours: only the last 37 count
        assertEquals(37, analyzer.look(runs, now, 180)!!.runs)
    }

    @Test
    fun `a temp target means the hours are not rated`() {
        val runs = series()
        runs[20] = run(runs[20].timestamp, 100.0, tempTarget = true)
        val hours = analyzer.look(runs, now, 180)!!

        assertTrue(hours.hadTempTarget)
        assertFalse(hours.used)
    }

    @Test
    fun `a log that began late is incomplete`() {
        // only the last hour: the first run is 2 hours after the start of the time looked at
        val hours = analyzer.look(series().takeLast(13), now, 180)!!

        assertTrue(hours.incomplete)
        assertFalse(hours.used)
    }

    @Test
    fun `a loop that stopped is incomplete`() {
        // the last run is an hour old
        val hours = analyzer.look(series().take(25), now, 180)!!

        assertTrue(hours.incomplete)
    }

    @Test
    fun `runs outside the time looked at are ignored, and too few runs give nothing`() {
        assertNull(analyzer.look(series().take(3), now, 180))
        val old = List(40) { run(from - 400 * minute + it * fiveMinutes, 100.0) }
        assertNull(analyzer.look(old, now, 180))
    }
}
