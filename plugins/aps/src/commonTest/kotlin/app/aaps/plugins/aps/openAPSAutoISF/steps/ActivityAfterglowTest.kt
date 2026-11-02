package app.aaps.plugins.aps.openAPSAutoISF.steps

import app.aaps.plugins.aps.openAPSAutoISF.steps.ActivityAfterglow.Level
import app.aaps.plugins.aps.openAPSAutoISF.steps.ActivityAfterglow.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityAfterglowTest {

    private val minute = 60_000L

    /** The memory uses 0 for "never", so the tests start at a real time. */
    private val t0 = 1_000 * minute

    private fun w(s5: Int = 0, s10: Int = 0, s15: Int = 0, s30: Int = 0, s60: Int = 0, s180: Int = 0) =
        StepWindows(s5, s10, s15, s30, s60, s180, timestamp = 0L)

    /** A walk of 100 steps/min for [minutes]: the sums as they are at the end of it. */
    private fun walk(minutes: Int) = w(
        s5 = 500, s10 = 1000, s15 = 1500,
        s30 = 100 * minOf(minutes, 30), s60 = 100 * minOf(minutes, 60), s180 = 100 * minOf(minutes, 180),
    )

    /** Standing still after a walk: the 5 and 10 minute sums are 0, the long sums stay high. */
    private fun still(s30: Int = 3000, s60: Int = 6000, s180: Int = 6000) = w(s30 = s30, s60 = s60, s180 = s180)

    @Test
    fun `the levels follow the agreed step sums`() {
        assertEquals(Level.NONE, ActivityAfterglow.classify(w(s60 = 2499, s30 = 1999)))
        assertEquals(Level.MILD, ActivityAfterglow.classify(w(s60 = 2500)))
        assertEquals(Level.GUARD, ActivityAfterglow.classify(w(s30 = 2000)))
        assertEquals(Level.GUARD, ActivityAfterglow.classify(w(s60 = 4000)))
        assertEquals(Level.GUARD, ActivityAfterglow.classify(w(s5 = 400, s10 = 800)))
        assertEquals(Level.NONE, ActivityAfterglow.classify(w(s5 = 400, s10 = 700)))
        assertEquals(Level.LONG, ActivityAfterglow.classify(w(s60 = 6000)))
        assertEquals(Level.LONG, ActivityAfterglow.classify(w(s180 = 7000)))
        assertEquals(Level.GUARD, ActivityAfterglow.classify(w(s180 = 6999, s60 = 4000)))
    }

    @Test
    fun `a few steps at home start nothing`() {
        val (r, m) = ActivityAfterglow.assess(w(s5 = 30, s10 = 60, s60 = 800), 120.0, 0.0, t0, ActivityAfterglow.Memory())
        assertEquals(Phase.NONE, r.phase)
        assertEquals(Level.NONE, m.level)
    }

    @Test
    fun `while walking at guard level there is no SMB and no basal`() {
        val (r, _) = ActivityAfterglow.assess(walk(40), 120.0, 0.0, t0, ActivityAfterglow.Memory())
        assertEquals(Level.GUARD, r.level)
        assertEquals(Phase.MOVING, r.phase)
        assertTrue(r.smbBlocked)
        assertEquals(0.0, r.basalFactor)
    }

    @Test
    fun `while walking at mild level the SMB is x0_8 and the basal is 0`() {
        val (r, _) = ActivityAfterglow.assess(w(s5 = 500, s60 = 2600), 120.0, 0.0, t0, ActivityAfterglow.Memory())
        assertEquals(Level.MILD, r.level)
        assertEquals(Phase.MOVING, r.phase)
        assertFalse(r.smbBlocked)
        assertEquals(0.8, r.smbFactor)
        assertEquals(0.0, r.basalFactor)
    }

    @Test
    fun `after a guard walk comes the hold, then the taper from 50 percent to 100 percent`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(walk(40), 120.0, 0.0, t0, m).second

        // 5 minutes later: hold
        val (hold, m1) = ActivityAfterglow.assess(still(), 120.0, 0.0, t0 + 5 * minute, m)
        assertEquals(Phase.HOLD, hold.phase)
        assertTrue(hold.smbBlocked)
        assertEquals(0.0, hold.basalFactor)

        // 10 minutes: the taper starts at 0.5
        val (t0, m2) = ActivityAfterglow.assess(still(), 120.0, 0.0, t0 + 10 * minute, m1)
        assertEquals(Phase.TAPER, t0.phase)
        assertFalse(t0.smbBlocked)
        assertEquals(0.5, t0.smbFactor, 1e-9)
        assertEquals(0.5, t0.basalFactor, 1e-9)

        // 40 minutes: halfway (30 of 60 minutes) = 0.75
        val (half, m3) = ActivityAfterglow.assess(still(), 120.0, 0.0, t0 + 40 * minute, m2)
        assertEquals(0.75, half.smbFactor, 1e-9)

        // after 70 minutes the taper is over, although the long sums are still high
        val (done, m4) = ActivityAfterglow.assess(still(), 120.0, 0.0, t0 + 71 * minute, m3)
        assertEquals(Phase.NONE, done.phase)
        assertEquals(1.0, done.smbFactor)
        assertEquals(1.0, done.basalFactor)
        assertEquals(Level.NONE, m4.level)
    }

    @Test
    fun `a mild walk has no hold`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(w(s5 = 500, s60 = 2600), 120.0, 0.0, t0, m).second
        val (r, _) = ActivityAfterglow.assess(still(s30 = 1500, s60 = 2600, s180 = 2600), 120.0, 0.0, t0 + minute, m)
        assertEquals(Phase.TAPER, r.phase)
        assertEquals(0.8, r.smbFactor, 0.05)
    }

    @Test
    fun `a long walk tapers for 120 minutes`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(w(s5 = 500, s10 = 1000, s15 = 1500, s60 = 6200, s180 = 7000), 120.0, 0.0, t0, m).second
        assertEquals(Level.LONG, m.level)
        val (r, _) = ActivityAfterglow.assess(still(), 120.0, 0.0, t0 + 70 * minute, m)
        assertEquals(Phase.TAPER, r.phase)
        assertEquals(0.75, r.smbFactor, 1e-9)
    }

    @Test
    fun `a few steps at home during the taper do not restart the hold`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(walk(40), 120.0, 0.0, t0, m).second
        // 30 minutes later, 120 steps in 5 minutes (a trip to the kitchen), 15 minute rate 8 steps/min
        val (r, _) = ActivityAfterglow.assess(w(s5 = 120, s10 = 130, s15 = 120, s30 = 2000, s60 = 5000, s180 = 5000), 120.0, 0.0, t0 + 30 * minute, m)
        assertEquals(Phase.TAPER, r.phase)
    }

    @Test
    fun `a real walk during the taper starts the movement phase again`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(walk(40), 120.0, 0.0, t0, m).second
        val (r, m1) = ActivityAfterglow.assess(w(s5 = 500, s10 = 1000, s15 = 1500, s30 = 2500, s60 = 4000, s180 = 4000), 120.0, 0.0, t0 + 30 * minute, m)
        assertEquals(Phase.MOVING, r.phase)
        assertEquals(t0 + 30 * minute, m1.lastMovingMs)
    }

    @Test
    fun `high and rising glucose halves the taper`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(walk(40), 120.0, 0.0, t0, m).second
        // 40 minutes: 30 into a 30 minute taper, so it is over
        val (r, _) = ActivityAfterglow.assess(still(), 200.0, 3.0, t0 + 40 * minute, m)
        assertEquals(Phase.NONE, r.phase)
        // the same time with glucose high but falling: the full taper is kept
        val (r2, _) = ActivityAfterglow.assess(still(), 200.0, -1.0, t0 + 40 * minute, m)
        assertEquals(Phase.TAPER, r2.phase)
        assertEquals(0.75, r2.smbFactor, 1e-9)
    }

    @Test
    fun `low or fast falling glucose keeps the SMB at 0 in the taper but the basal follows the ramp`() {
        var m = ActivityAfterglow.Memory()
        m = ActivityAfterglow.assess(walk(40), 120.0, 0.0, t0, m).second
        val (low, _) = ActivityAfterglow.assess(still(), 80.0, 0.0, t0 + 40 * minute, m)
        assertTrue(low.smbBlocked)
        assertEquals(0.75, low.basalFactor, 1e-9)
        val (fall, _) = ActivityAfterglow.assess(still(), 130.0, -5.0, t0 + 40 * minute, m)
        assertTrue(fall.smbBlocked)
        val (ok, _) = ActivityAfterglow.assess(still(), 130.0, -4.0, t0 + 40 * minute, m)
        assertFalse(ok.smbBlocked)
    }
}
