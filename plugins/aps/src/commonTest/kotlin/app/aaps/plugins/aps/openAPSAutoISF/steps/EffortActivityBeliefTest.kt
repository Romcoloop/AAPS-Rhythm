package app.aaps.plugins.aps.openAPSAutoISF.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffortActivityBeliefTest {

    private val minute = 60_000L

    /** Not 0: a memory stamped at 0 reads as "no memory" (lastEffortMs 0 is the empty mark). */
    private val t0 = 1_700_000_000_000L

    private fun w(s5: Int, s15: Int, s60: Int = 0) = StepWindows(s5, 0, s15, 0, s60, 0, timestamp = 0L)

    @Test
    fun `no movement means no reduction`() {
        val (a, m) = EffortActivityBelief.assess(w(0, 0), nowMs = 0L, prior = EffortActivityBelief.Memory())
        assertEquals(EffortActivityBelief.State.IDLE, a.state)
        assertEquals(1.0, a.smbFactor)
        assertEquals(EffortActivityBelief.Memory(), m)
    }

    @Test
    fun `below 25 steps per minute is still idle`() {
        val (a, _) = EffortActivityBelief.assess(w(s5 = 120, s15 = 360), 0L, EffortActivityBelief.Memory())
        assertEquals(EffortActivityBelief.State.IDLE, a.state)
        assertEquals(1.0, a.smbFactor)
    }

    @Test
    fun `a brisk walk opens the full reduction`() {
        // 60 steps/min over 5 and 15 minutes, 60 minute window at walking pace too
        val (a, _) = EffortActivityBelief.assess(w(s5 = 300, s15 = 900, s60 = 2400), 0L, EffortActivityBelief.Memory())
        assertEquals(EffortActivityBelief.State.ACTIVE, a.state)
        assertEquals(EffortActivityBelief.SMB_FLOOR, a.smbFactor, 1e-9)
    }

    @Test
    fun `a short burst reduces now but does not arm the memory`() {
        // 5 minute window says moving, 15 minute window does not
        val (a, m) = EffortActivityBelief.assess(w(s5 = 200, s15 = 250), t0, EffortActivityBelief.Memory())
        assertEquals(EffortActivityBelief.State.ACTIVE, a.state)
        assertTrue(a.smbFactor < 1.0)
        assertTrue(a.reasons.contains("burst_unconfirmed"))
        assertEquals(0L, m.lastEffortMs)
        // one run later, still: no protection left
        val (b, _) = EffortActivityBelief.assess(w(0, 0), t0 + 5 * minute, m)
        assertEquals(EffortActivityBelief.State.IDLE, b.state)
        assertEquals(1.0, b.smbFactor)
    }

    @Test
    fun `a confirmed walk keeps a fading protection after it ends`() {
        var memory = EffortActivityBelief.Memory()
        var now = t0
        // 30 minutes of walking at 40 steps/min, one run every 5 minutes
        repeat(7) {
            val (a, m) = EffortActivityBelief.assess(w(s5 = 200, s15 = 600, s60 = 1200), now, memory)
            assertEquals(EffortActivityBelief.State.ACTIVE, a.state)
            memory = m
            now += 5 * minute
        }
        assertEquals(EffortActivityBelief.EFFORT_MINUTES_CAP, memory.effortMinutes)
        assertEquals(EffortActivityBelief.EFFORT_MEMORY_MAX_MIN, EffortActivityBelief.memoryHorizonMin(memory.effortMinutes))

        // the walk stops: recent effort, fading over 120 minutes
        val (rightAfter, m1) = EffortActivityBelief.assess(w(0, 0), now, memory)
        assertEquals(EffortActivityBelief.State.RECENT_EFFORT, rightAfter.state)
        assertTrue(rightAfter.smbFactor < 0.6)
        val (hourLater, m2) = EffortActivityBelief.assess(w(0, 0), now + 60 * minute, m1)
        assertEquals(EffortActivityBelief.State.RECENT_EFFORT, hourLater.state)
        assertTrue(hourLater.smbFactor > rightAfter.smbFactor)
        val (twoHoursLater, _) = EffortActivityBelief.assess(w(0, 0), now + 121 * minute, m2)
        assertEquals(EffortActivityBelief.State.IDLE, twoHoursLater.state)
        assertEquals(1.0, twoHoursLater.smbFactor)
    }

    @Test
    fun `a short confirmed walk earns a short protection`() {
        val (_, m) = EffortActivityBelief.assess(w(s5 = 200, s15 = 400), t0, EffortActivityBelief.Memory())
        assertEquals(EffortActivityBelief.EFFORT_SEED_MIN, m.effortMinutes)
        val horizon = EffortActivityBelief.memoryHorizonMin(m.effortMinutes)
        assertEquals(45.0, horizon)
        val (still, _) = EffortActivityBelief.assess(w(0, 0), t0 + 44 * minute, m)
        assertEquals(EffortActivityBelief.State.RECENT_EFFORT, still.state)
        val (gone, _) = EffortActivityBelief.assess(w(0, 0), t0 + 46 * minute, m)
        assertEquals(EffortActivityBelief.State.IDLE, gone.state)
    }

    @Test
    fun `the factor never goes above 1 or below the floor`() {
        val (huge, m) = EffortActivityBelief.assess(w(s5 = 5000, s15 = 15000, s60 = 60000), t0, EffortActivityBelief.Memory())
        assertEquals(EffortActivityBelief.SMB_FLOOR, huge.smbFactor, 1e-9)
        val (later, _) = EffortActivityBelief.assess(w(0, 0), t0 + 10 * minute, m)
        assertTrue(later.smbFactor in EffortActivityBelief.SMB_FLOOR..1.0)
    }
}
