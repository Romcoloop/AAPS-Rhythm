package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The pure parts of [AutoIsfLogAnalysis]: which slot a run name belongs to, and which slot all runs used. */
class AutoIsfLogAnalysisTest {

    @Test
    fun `a slot label from the log gives its slot`() {
        assertEquals(Slot.BREAKFAST, AutoIsfLogAnalysis.slotForName("Breakfast"))
        assertEquals(Slot.LUNCH_WEEKEND, AutoIsfLogAnalysis.slotForName("Lunch-weekend"))
        assertEquals(Slot.NIGHT, AutoIsfLogAnalysis.slotForName("Night"))
    }

    @Test
    fun `no slot line, none or an unknown name mean the global values`() {
        assertNull(AutoIsfLogAnalysis.slotForName(null))
        assertNull(AutoIsfLogAnalysis.slotForName("none"))
        assertNull(AutoIsfLogAnalysis.slotForName("Brunch"))
    }

    @Test
    fun `every slot is found by its own label`() {
        Slot.entries.forEach { assertEquals(it, AutoIsfLogAnalysis.slotForName(it.label)) }
    }

    private fun run(slotName: String?) = AutoIsfLogRun(
        timestamp = 1L,
        bgTimestamp = 1L,
        bg = 100.0,
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
        slotName = slotName
    )

    @Test
    fun `all runs in one slot give that slot`() {
        assertEquals(Slot.LUNCH, AutoIsfLogAnalysis.slotOfRuns(listOf(run("Lunch"), run("Lunch"), run("Lunch"))))
    }

    @Test
    fun `two slots, a slot and the global values, or only the global values give no slot`() {
        assertNull(AutoIsfLogAnalysis.slotOfRuns(listOf(run("Lunch"), run("Dinner"))))
        assertNull(AutoIsfLogAnalysis.slotOfRuns(listOf(run("Lunch"), run("none"))))
        assertNull(AutoIsfLogAnalysis.slotOfRuns(listOf(run("Lunch"), run(null))))
        assertNull(AutoIsfLogAnalysis.slotOfRuns(listOf(run("none"), run("none"))))
        assertNull(AutoIsfLogAnalysis.slotOfRuns(listOf(run(null), run(null))))
    }
}
