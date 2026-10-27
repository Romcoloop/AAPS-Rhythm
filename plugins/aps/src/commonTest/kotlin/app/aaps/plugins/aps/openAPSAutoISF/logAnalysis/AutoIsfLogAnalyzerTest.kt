package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoIsfLogAnalyzerTest {

    private val analyzer = AutoIsfLogAnalyzer()
    private val start = 1_800_000_000_000L
    private val fiveMinutes = 5 * 60_000L

    private val settings = mapOf(
        "autoISF_max" to 1.6, "bgAccel_ISF_weight" to 0.3, "higher_ISFrange_weight" to 0.6,
        "smb_delivery_ratio_max" to 0.8, "smb_max_range_extension" to 5.0
    )

    private val current = mapOf(
        AutoIsfParam.AutoIsfMax to 1.6,
        AutoIsfParam.BgAccelWeight to 0.3,
        AutoIsfParam.HighBgWeight to 0.6,
        AutoIsfParam.SmbRatioMax to 0.8,
        AutoIsfParam.SmbRangeExtension to 5.0
    )

    private fun run(
        index: Int,
        bg: Double,
        smb: Double = 0.0,
        limitedByMax: Boolean = false,
        acce: Double? = null,
        bgIsf: Double? = null,
        ratioAtMax: Boolean = false,
        maxBolus: Double? = null,
        minGuardStop: Boolean = false,
        eventualBg: Double = bg,
        settings: Map<String, Double> = this.settings
    ) = AutoIsfLogRun(
        timestamp = start + index * fiveMinutes + 9_000L,
        bgTimestamp = start + index * fiveMinutes,
        bg = bg,
        eventualBg = eventualBg,
        targetBg = 111.7,
        insulinReq = null,
        smbUnits = smb,
        iob = 0.5,
        finalIsfFactor = null,
        acceIsf = acce,
        bgIsf = bgIsf,
        ppIsf = null,
        duraIsf = null,
        limitedByAutoIsfMax = limitedByMax,
        evenTargetSmb = true,
        smbRatioAtMax = ratioAtMax,
        smbStoppedByMinGuard = minGuardStop,
        maxBolus = maxBolus,
        settings = settings
    )

    /** One day of flat BG at [bg], 288 runs. */
    private fun day(bg: Double = 120.0) = (0 until 288).map { run(it, bg) }.toMutableList()

    @Test
    fun noSuggestionsFromTooLittleData() {
        val runs = (0 until 24).map { run(it, if (it in 10..12) 60.0 else 150.0, smb = if (it == 5) 1.0 else 0.0, limitedByMax = true) }
        val report = analyzer.analyze(runs, 0, current)

        assertFalse(report.enoughData)
        assertTrue(report.suggestions.isEmpty())
        assertEquals(1, report.stats.lowEpisodesAfterSmb)
    }

    @Test
    fun keepsOneRunPerBgReading() {
        val first = run(0, 150.0)
        val withSmb = first.copy(timestamp = first.timestamp + 5_000L, smbUnits = 0.5)
        val after = first.copy(timestamp = first.timestamp + 10_000L)
        val kept = analyzer.oneRunPerBg(listOf(first, withSmb, after))

        assertEquals(1, kept.size)
        assertEquals(0.5, kept[0].smbUnits)
    }

    @Test
    fun lowsAfterMaxLimitedSmbsSuggestLowerAutoIsfMax() {
        val runs = day()
        // Three times: a big SMB limited by autoISF_max, then a low two hours later
        for (k in listOf(20, 100, 200)) {
            runs[k] = run(k, 190.0, smb = 1.0, limitedByMax = true, acce = 1.0, bgIsf = 1.3)
            for (j in k + 24..k + 27) runs[j] = run(j, 62.0)
        }
        val report = analyzer.analyze(runs, 0, current)

        assertTrue(report.enoughData)
        assertEquals(3, report.stats.lowEpisodesAfterSmb)
        val suggestion = assertNotNull(report.suggestions.firstOrNull { it.param == AutoIsfParam.AutoIsfMax })
        assertEquals(1.6, suggestion.current)
        assertEquals(1.5, suggestion.suggested, 1e-9)
        assertEquals(AutoIsfSuggestionReason.LowsAfterMaxLimitedSmb, suggestion.reason)
        assertEquals(3, suggestion.events)
        // Never more insulin when there are lows
        assertTrue(report.suggestions.all { it.suggested < it.current })
    }

    @Test
    fun lowsAfterAcceDrivenSmbsSuggestLowerAccelWeight() {
        val runs = day()
        for (k in listOf(30, 130, 230)) {
            runs[k] = run(k, 160.0, smb = 0.6, acce = 1.5, bgIsf = 1.2)
            runs[k + 20] = run(k + 20, 65.0)
        }
        val report = analyzer.analyze(runs, 0, current)

        val suggestion = assertNotNull(report.suggestions.firstOrNull { it.param == AutoIsfParam.BgAccelWeight })
        assertEquals(0.24, suggestion.suggested, 1e-9)
        assertFalse(report.suggestions.any { it.param == AutoIsfParam.AutoIsfMax })
    }

    @Test
    fun lowsAfterSmbAtMaxBolusSuggestSmallerRangeExtension() {
        val runs = day()
        for (k in listOf(30, 130, 230)) {
            runs[k] = run(k, 175.0, smb = 0.95, bgIsf = 1.3, ratioAtMax = true, maxBolus = 1.0)
            runs[k + 20] = run(k + 20, 66.0)
        }
        val report = analyzer.analyze(runs, 0, current)

        val params = report.suggestions.associate { it.param to it.suggested }
        assertEquals(4.0, params[AutoIsfParam.SmbRangeExtension])
        assertEquals(0.7, params.getValue(AutoIsfParam.SmbRatioMax), 1e-9)
        assertEquals(0.48, params.getValue(AutoIsfParam.HighBgWeight), 1e-9)
    }

    @Test
    fun predictedLowsCountWhenThereAreNoRealLows() {
        val runs = day()
        for (k in listOf(30, 130, 230)) {
            runs[k] = run(k, 175.0, smb = 0.95, bgIsf = 1.3, maxBolus = 1.0)
            runs[k + 3] = run(k + 3, 160.0, minGuardStop = true)
            runs[k + 4] = run(k + 4, 155.0, minGuardStop = true)        // same episode, counted once
        }
        val report = analyzer.analyze(runs, 0, current)

        assertEquals(0, report.stats.lowEpisodes)
        assertEquals(3, report.stats.predictedLowsAfterSmb)
        assertTrue(report.suggestions.any { it.param == AutoIsfParam.SmbRangeExtension && it.suggested == 4.0 })
    }

    @Test
    fun highsWithoutLowsAndMaxLimitSuggestHigherAutoIsfMax() {
        val runs = day()
        for (k in 0 until 100) runs[k] = run(k, 210.0, limitedByMax = true, eventualBg = 200.0)
        val report = analyzer.analyze(runs, 0, current)

        assertEquals(1, report.suggestions.size)
        val suggestion = report.suggestions[0]
        assertEquals(AutoIsfParam.AutoIsfMax, suggestion.param)
        assertEquals(1.7, suggestion.suggested, 1e-9)
    }

    @Test
    fun noMoreInsulinWhenAnyLowWasSeen() {
        val runs = day()
        for (k in 0 until 100) runs[k] = run(k, 210.0, limitedByMax = true, eventualBg = 200.0)
        runs[200] = run(200, 65.0)
        val report = analyzer.analyze(runs, 0, current)

        assertTrue(report.suggestions.isEmpty())
    }

    @Test
    fun onlyRunsAfterTheLastSettingsChangeAreUsed() {
        val old = settings + ("autoISF_max" to 2.0)
        val runs = day()
        for (k in 0 until 100) runs[k] = run(k, 60.0, settings = old)
        val report = analyzer.analyze(runs, 0, current)

        assertEquals(runs[100].timestamp, report.settingsChangedAt)
        assertEquals(188, report.stats.runs)
        assertEquals(0, report.stats.lowEpisodes)
    }

    @Test
    fun suggestionsStayInsideTheLimitsOfTheSetting() {
        val runs = day()
        for (k in listOf(20, 100, 200)) {
            runs[k] = run(k, 190.0, smb = 1.0, limitedByMax = true)
            runs[k + 24] = run(k + 24, 62.0)
        }
        val atMinimum = current + (AutoIsfParam.AutoIsfMax to 1.0)
        val report = analyzer.analyze(runs, 0, atMinimum)

        assertFalse(report.suggestions.any { it.param == AutoIsfParam.AutoIsfMax })
    }
}
