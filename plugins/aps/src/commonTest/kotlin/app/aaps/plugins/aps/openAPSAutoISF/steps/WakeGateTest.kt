package app.aaps.plugins.aps.openAPSAutoISF.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WakeGateTest {

    private val start = 3 * 60
    private val end = 10 * 60
    private val n = 150

    private fun w(s60: Int) = StepWindows(0, 0, 0, 0, s60, 0, timestamp = 0L)

    private fun eval(prior: WakeGate.State, w: StepWindows?, minute: Int) =
        WakeGate.evaluate(prior, w, minute, n, start, end)

    @Test
    fun `outside the window nothing is blocked and the latch is reset`() {
        val r = eval(WakeGate.State(awake = true), w(0), 2 * 60)
        assertFalse(r.inWindow)
        assertFalse(r.blockSmb)
        assertFalse(r.state.awake)
        assertFalse(eval(WakeGate.State(), w(0), 10 * 60).inWindow)
    }

    @Test
    fun `asleep in the window blocks the SMB`() {
        val r = eval(WakeGate.State(), w(40), 5 * 60)
        assertTrue(r.inWindow)
        assertTrue(r.blockSmb)
        assertFalse(r.state.awake)
    }

    @Test
    fun `enough steps in one hour open the gate and it stays open while sitting still`() {
        val up = eval(WakeGate.State(), w(160), 6 * 60 + 30)
        assertFalse(up.blockSmb)
        assertTrue(up.state.awake)
        val breakfast = eval(up.state, w(0), 7 * 60 + 15)
        assertFalse(breakfast.blockSmb)
        assertTrue(breakfast.state.awake)
    }

    @Test
    fun `no step data blocks nothing`() {
        val r = eval(WakeGate.State(), null, 5 * 60)
        assertFalse(r.blockSmb)
        assertEquals(WakeGate.State(), r.state)
    }

    @Test
    fun `a window with start after end is off`() {
        val r = WakeGate.evaluate(WakeGate.State(), w(0), 5 * 60, n, startMinute = 10 * 60, endMinute = 3 * 60)
        assertFalse(r.inWindow)
        assertFalse(r.blockSmb)
    }

    @Test
    fun `decide carries the closed gate and logs it`() {
        val closed = eval(WakeGate.State(), w(10), 5 * 60)
        val d = StepsActivityRules.decide(w(10), 100.0, 60.0, false, null, null, closed)
        assertTrue(d.wakeGateClosed)
        assertTrue(d.active)
        assertTrue(d.log.contains("wake gate: asleep"))
        val open = eval(WakeGate.State(awake = true), w(10), 5 * 60)
        val e = StepsActivityRules.decide(w(10), 100.0, 60.0, false, null, null, open)
        assertFalse(e.wakeGateClosed)
        assertTrue(e.log.contains("wake gate: awake"))
    }
}
