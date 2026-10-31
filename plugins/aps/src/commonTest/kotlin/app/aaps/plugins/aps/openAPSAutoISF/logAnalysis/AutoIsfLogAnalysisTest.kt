package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure parts of [AutoIsfLogAnalysis]: which meals belong to a slot, and the periods offered. */
class AutoIsfLogAnalysisTest {

    private fun run(timestamp: Long, slotName: String?) = AutoIsfLogRun(
        timestamp = timestamp,
        bgTimestamp = timestamp,
        bg = 120.0,
        eventualBg = null,
        targetBg = 100.0,
        insulinReq = null,
        smbUnits = 0.0,
        iob = null,
        finalIsfFactor = null,
        acceIsf = null,
        bgIsf = null,
        ppIsf = null,
        duraIsf = null,
        limitedByAutoIsfMax = false,
        evenTargetSmb = false,
        smbRatioAtMax = false,
        smbStoppedByMinGuard = false,
        maxBolus = null,
        settings = null,
        cob = null,
        tempTarget = false,
        slotName = slotName
    )

    @Test
    fun `a meal belongs to the slot its runs name`() {
        val breakfastThenTail = listOf(run(1, "Breakfast"), run(2, "Breakfast"), run(3, "none"))
        assertTrue(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST, breakfastThenTail))
        assertFalse(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST_WEEKEND, breakfastThenTail))
        assertFalse(AutoIsfLogAnalysis.belongsToSlot(Slot.LUNCH, breakfastThenTail))

        // the weekday breakfast window on a Saturday: the runs name the weekend slot, or nothing
        val saturday = listOf(run(1, "none"), run(2, "Breakfast-weekend"), run(3, "none"))
        assertFalse(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST, saturday))
        assertTrue(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST_WEEKEND, saturday))
        assertTrue(AutoIsfLogAnalysis.belongsToSlot(Slot.NIGHT, listOf(run(1, "Night"), run(2, "none"))))
    }

    @Test
    fun `runs of an older build without a slot line count for a weekday slot only`() {
        val old = listOf(run(1, null), run(2, null))
        assertTrue(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST, old))
        assertTrue(AutoIsfLogAnalysis.belongsToSlot(Slot.DINNER, old))
        assertFalse(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST_WEEKEND, old))
        assertFalse(AutoIsfLogAnalysis.belongsToSlot(Slot.NIGHT, old))
        // one named run is enough to decide
        assertFalse(AutoIsfLogAnalysis.belongsToSlot(Slot.BREAKFAST, listOf(run(1, null), run(2, "none"))))
    }

    @Test
    fun `weekend slots offer longer periods`() {
        assertEquals(listOf(1, 3, 7), AutoIsfLogAnalysis.dayChoices(null))
        assertEquals(listOf(1, 3, 7), AutoIsfLogAnalysis.dayChoices(Slot.LUNCH))
        assertEquals(listOf(1, 3, 7), AutoIsfLogAnalysis.dayChoices(Slot.NIGHT))
        assertEquals(listOf(14, 28), AutoIsfLogAnalysis.dayChoices(Slot.LUNCH_WEEKEND))
        assertTrue(AutoIsfLogAnalysis.DEFAULT_DAYS in AutoIsfLogAnalysis.dayChoices(null))
    }
}
