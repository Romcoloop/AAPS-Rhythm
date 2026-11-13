package app.aaps.plugins.aps.openAPSAutoISF.steps

import app.aaps.core.data.model.SC
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A watch sends its steps in one of two forms. The form depends on the AAPS version of the watch app:
 * before AAPS #5499 the 10 and 15 minute values are single 5 minute blocks, from #5499 on they are
 * sums. Reading sums as blocks counts steps two and three times, and the sport guard and the effort
 * protection would then cut insulin too early. These tests check that
 * [AutoIsfSteps.watchSendsSums] tells the two forms apart from the stored rows alone.
 */
class WatchStepFormatTest {

    private val watch = "Test watch"
    private val now = 1_700_000_000_000L
    private val lengths = listOf(5, 10, 15, 30, 60, 180)

    /** One sample of a watch app before AAPS #5499: six rows, each filled up to its own length only. */
    private fun blockSample(
        timestamp: Long,
        device: String = watch,
        block5: Int = 548, block10: Int = 193, block15: Int = 100,
        steps30: Int = 1672, steps60: Int = 1890, steps180: Int = 2245,
    ): List<SC> = lengths.map { minutes ->
        SC(
            duration = minutes * 60_000L,
            timestamp = timestamp,
            steps5min = block5,
            steps10min = if (minutes >= 10) block10 else 0,
            steps15min = if (minutes >= 15) block15 else 0,
            steps30min = if (minutes >= 30) steps30 else 0,
            steps60min = if (minutes >= 60) steps60 else 0,
            steps180min = if (minutes >= 180) steps180 else 0,
            device = device,
        )
    }

    /** One sample of a watch app from AAPS #5499 on: six rows, every row has all six sums. */
    private fun sumSample(
        timestamp: Long,
        device: String = watch,
        steps5: Int = 548, steps10: Int = 741, steps15: Int = 841,
        steps30: Int = 1672, steps60: Int = 1890, steps180: Int = 2245,
    ): List<SC> = lengths.map { minutes ->
        SC(
            duration = minutes * 60_000L,
            timestamp = timestamp,
            steps5min = steps5,
            steps10min = steps10,
            steps15min = steps15,
            steps30min = steps30,
            steps60min = steps60,
            steps180min = steps180,
            device = device,
        )
    }

    private fun List<SC>.longRow(timestamp: Long, device: String = watch): SC =
        first { it.duration == 180 * 60_000L && it.timestamp == timestamp && it.device == device }

    @Test
    fun `an older watch app is read as blocks`() {
        val rows = blockSample(now)
        assertFalse(AutoIsfSteps.watchSendsSums(rows.longRow(now), rows))
    }

    @Test
    fun `a newer watch app is read as sums`() {
        val rows = sumSample(now)
        assertTrue(AutoIsfSteps.watchSendsSums(rows.longRow(now), rows))
    }

    @Test
    fun `steps only in the 5 minutes that still run are enough to see a newer watch app`() {
        // No finished block has steps yet, so 5, 10 and 15 minutes are 0. The longer sums have them.
        val rows = sumSample(now, steps5 = 0, steps10 = 0, steps15 = 0, steps30 = 40, steps60 = 40, steps180 = 40)
        assertTrue(AutoIsfSteps.watchSendsSums(rows.longRow(now), rows))
    }

    @Test
    fun `a watch without any steps is read as blocks and that gives the same zeros`() {
        val rows = sumSample(now, steps5 = 0, steps10 = 0, steps15 = 0, steps30 = 0, steps60 = 0, steps180 = 0)
        assertFalse(AutoIsfSteps.watchSendsSums(rows.longRow(now), rows))
    }

    @Test
    fun `after an update of the watch app the rows of the newest sample decide`() {
        val rows = blockSample(now - 40_000L) + sumSample(now)
        assertTrue(AutoIsfSteps.watchSendsSums(rows.longRow(now), rows))
    }

    @Test
    fun `after a step back to an older watch app the rows of the newest sample decide`() {
        val rows = sumSample(now - 40_000L) + blockSample(now)
        assertFalse(AutoIsfSteps.watchSendsSums(rows.longRow(now), rows))
    }

    @Test
    fun `older samples decide when the newest sample has only its 180 minute row`() {
        val loneRow = sumSample(now).longRow(now)

        val afterSums = sumSample(now - 40_000L) + loneRow
        assertTrue(AutoIsfSteps.watchSendsSums(loneRow, afterSums))

        val afterBlocks = blockSample(now - 40_000L) + loneRow
        assertFalse(AutoIsfSteps.watchSendsSums(loneRow, afterBlocks))
    }

    @Test
    fun `a 180 minute row alone is read as blocks as before`() {
        val loneRow = blockSample(now).longRow(now)
        assertFalse(AutoIsfSteps.watchSendsSums(loneRow, listOf(loneRow)))
    }

    @Test
    fun `rows of another device do not decide`() {
        val loneRow = blockSample(now).longRow(now)
        val rows = sumSample(now, device = "Other watch") + loneRow
        assertFalse(AutoIsfSteps.watchSendsSums(loneRow, rows))
    }
}
