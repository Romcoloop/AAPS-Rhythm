package app.aaps.plugins.aps.openAPSAutoISF.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepsActivityRulesTest {

    private fun w(s5: Int = 0, s10: Int = 0, s15: Int = 0, s30: Int = 0, s60: Int = 0, s180: Int = 0) =
        StepWindows(s5, s10, s15, s30, s60, s180, timestamp = 0L)

    @Test
    fun `no data fires no rule`() {
        assertFalse(StepsActivityRules.sportGuard(null))
        assertEquals(0, StepsActivityRules.smbIntervalFloorMinutes(null, 100.0, 60.0))
        assertEquals(StepsActivity.NONE, StepsActivityRules.decide(null, 100.0, 60.0, true, true, null))
    }

    @Test
    fun `a burst of steps starts the sport guard`() {
        assertTrue(StepsActivityRules.sportGuard(w(s5 = 400, s10 = 800)))
        assertFalse(StepsActivityRules.sportGuard(w(s5 = 400, s10 = 700)))
        assertFalse(StepsActivityRules.sportGuard(w(s5 = 300, s10 = 800)))
    }

    @Test
    fun `sustained activity starts the sport guard`() {
        assertTrue(StepsActivityRules.sportGuard(w(s5 = 1, s30 = 1200)))
        assertTrue(StepsActivityRules.sportGuard(w(s5 = 1, s60 = 3000)))
        assertTrue(StepsActivityRules.sportGuard(w(s5 = 1, s180 = 4500)))
        assertFalse(StepsActivityRules.sportGuard(w(s5 = 1, s30 = 1199, s60 = 2999, s180 = 4499)))
    }

    @Test
    fun `zero steps in the last 5 minutes release the sport guard at once`() {
        assertFalse(StepsActivityRules.sportGuard(w(s5 = 0, s30 = 2000, s60 = 4000, s180 = 9000)))
    }

    @Test
    fun `after activity the SMB interval is at least 10 minutes below 120`() {
        assertEquals(10, StepsActivityRules.smbIntervalFloorMinutes(w(s180 = 1501), 119.0, 60.0))
        assertEquals(0, StepsActivityRules.smbIntervalFloorMinutes(w(s180 = 1501), 120.0, 60.0))
        assertEquals(0, StepsActivityRules.smbIntervalFloorMinutes(w(s180 = 1500), 100.0, 60.0))
    }

    @Test
    fun `while still moving the SMB interval is at least 15 minutes`() {
        assertEquals(15, StepsActivityRules.smbIntervalFloorMinutes(w(s5 = 101, s30 = 501), 150.0, 21.0))
        // the last SMB must be more than 20 minutes old
        assertEquals(0, StepsActivityRules.smbIntervalFloorMinutes(w(s5 = 101, s30 = 501), 150.0, 20.0))
        // both rules: the larger one wins
        assertEquals(15, StepsActivityRules.smbIntervalFloorMinutes(w(s5 = 101, s30 = 501, s180 = 2000), 100.0, 30.0))
    }

    @Test
    fun `decide respects every switch`() {
        val walk = w(s5 = 400, s10 = 800, s30 = 1200, s60 = 1500, s180 = 2000)
        val off = StepsActivityRules.decide(walk, 100.0, 60.0, sportGuardEnabled = false, smbIntervalEnabled = false, effort = null)
        assertFalse(off.active)
        assertFalse(off.sportGuard)
        assertEquals(0, off.smbIntervalFloorMinutes)
        assertEquals(1.0, off.effortSmbFactor)
        assertTrue(off.log.startsWith("steps 5m=400"))

        val on = StepsActivityRules.decide(walk, 100.0, 60.0, sportGuardEnabled = true, smbIntervalEnabled = true, effort = null)
        assertTrue(on.sportGuard)
        // still moving (400/5 min, 1200/30 min, last SMB 60 min ago) wins over the 10 minutes after activity
        assertEquals(15, on.smbIntervalFloorMinutes)
        assertTrue(on.active)
        assertTrue(on.log.contains("sport guard"))
        assertTrue(on.log.contains("at least 15m"))
    }

    @Test
    fun `the effort factor of the belief is passed on`() {
        val (assessment, _) = EffortActivityBelief.assess(w(s5 = 250, s15 = 600, s60 = 600), nowMs = 0L, prior = EffortActivityBelief.Memory())
        val d = StepsActivityRules.decide(w(s5 = 250, s15 = 600, s60 = 600), 100.0, 60.0, false, false, assessment)
        assertEquals(assessment.smbFactor, d.effortSmbFactor)
        assertTrue(d.active)
        assertTrue(d.log.contains("effort x"))
    }

    @Test
    fun `formatFactor keeps two decimals`() {
        assertEquals("0.45", StepsActivityRules.formatFactor(0.45))
        assertEquals("1.00", StepsActivityRules.formatFactor(1.0))
        assertEquals("0.70", StepsActivityRules.formatFactor(0.699))
    }
}
