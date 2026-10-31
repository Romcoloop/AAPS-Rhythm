package app.aaps.plugins.aps.openAPSAutoISF.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StepBucketsTest {

    private val minute = StepBuckets.MINUTE_MS
    private val t0 = 1_700_000_000_000L

    @Test
    fun `no event means no data`() {
        assertNull(StepBuckets().windows(t0))
    }

    @Test
    fun `first event only sets the base line`() {
        val b = StepBuckets()
        b.add(5000, t0)
        val w = assertNotNull(b.windows(t0))
        assertEquals(0, w.steps5)
        assertEquals(0, w.steps180)
    }

    @Test
    fun `steps are booked to the minute of the event and summed per window`() {
        val b = StepBuckets()
        b.add(1000, t0)
        b.add(1100, t0 + 1 * minute)   // 100 steps in minute 1
        b.add(1300, t0 + 4 * minute)   // 200 steps in minute 4
        b.add(1310, t0 + 12 * minute)  // 10 steps in minute 12
        val w = assertNotNull(b.windows(t0 + 12 * minute))
        assertEquals(10, w.steps5)      // minutes 8..12
        assertEquals(210, w.steps10)    // minutes 3..12
        assertEquals(310, w.steps15)    // minutes 0..12 (only 13 minutes exist)
        assertEquals(310, w.steps180)
    }

    @Test
    fun `a counter that starts again sets a new base line and books nothing`() {
        val b = StepBuckets()
        b.add(5000, t0)
        b.add(5100, t0 + minute)
        b.add(20, t0 + 2 * minute)     // reboot: counter dropped
        b.add(70, t0 + 3 * minute)     // 50 steps after the new base line
        val w = assertNotNull(b.windows(t0 + 3 * minute))
        assertEquals(150, w.steps5)
    }

    @Test
    fun `old minutes fall out of the three hour window`() {
        val b = StepBuckets()
        b.add(0, t0)
        b.add(500, t0 + minute)
        b.add(500, t0 + 200 * minute)
        val w = assertNotNull(b.windows(t0 + 200 * minute))
        assertEquals(0, w.steps180)
    }

    @Test
    fun `clear forgets everything`() {
        val b = StepBuckets()
        b.add(0, t0)
        b.add(50, t0 + minute)
        b.clear()
        assertNull(b.windows(t0 + minute))
    }
}
