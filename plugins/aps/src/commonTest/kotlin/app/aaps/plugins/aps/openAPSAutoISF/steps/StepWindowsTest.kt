package app.aaps.plugins.aps.openAPSAutoISF.steps

import kotlin.test.Test
import kotlin.test.assertEquals

class StepWindowsTest {

    @Test
    fun `watch blocks are added up to sums`() {
        // The values of a real watch: 548 steps in the newest block, 193 in the one before it.
        val w = StepWindows.fromWatch(
            block5 = 548, block10 = 193, block15 = 100,
            steps30 = 1672, steps60 = 1890, steps180 = 2245, timestamp = 5L,
        )
        assertEquals(548, w.steps5)
        assertEquals(741, w.steps10)
        assertEquals(841, w.steps15)
        assertEquals(1672, w.steps30)
        assertEquals(1890, w.steps60)
        assertEquals(2245, w.steps180)
        assertEquals(5L, w.timestamp)
        assertEquals(StepSource.WATCH, w.source)
    }

    @Test
    fun `the pace over 15 minutes uses the summed window`() {
        val w = StepWindows.fromWatch(block5 = 300, block10 = 300, block15 = 300, steps30 = 900, steps60 = 900, steps180 = 900, timestamp = 0L)
        assertEquals(60.0, w.rate15, 1e-9)
    }

    @Test
    fun `windows are from the phone unless told otherwise`() {
        assertEquals(StepSource.PHONE, StepWindows(1, 2, 3, 4, 5, 6, timestamp = 0L).source)
    }
}
