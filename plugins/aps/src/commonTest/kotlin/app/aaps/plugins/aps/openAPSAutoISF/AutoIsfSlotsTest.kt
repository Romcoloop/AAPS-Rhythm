package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoIsfSlotsTest {

    private val windows = mapOf(
        Slot.BREAKFAST to "05:30-08:00",
        Slot.LUNCH to "11:00-14:00",
        Slot.DINNER to "17:00-20:00"
    )

    private fun select(enabled: Boolean, hour: Int, minute: Int = 0): Slot? =
        AutoIsfSlots.select(enabled, hour * 60 + minute) { windows.getValue(it) }

    @Test
    fun `slots off means no slot`() {
        assertNull(select(enabled = false, hour = 12))
    }

    @Test
    fun `slot is chosen by the clock`() {
        assertEquals(Slot.BREAKFAST, select(enabled = true, hour = 5, minute = 30))
        assertEquals(Slot.BREAKFAST, select(enabled = true, hour = 7, minute = 59))
        assertEquals(Slot.LUNCH, select(enabled = true, hour = 12))
        assertEquals(Slot.DINNER, select(enabled = true, hour = 19, minute = 30))
    }

    @Test
    fun `outside all meal times means global values`() {
        assertNull(select(enabled = true, hour = 8))       // end is not included
        assertNull(select(enabled = true, hour = 15))
        assertNull(select(enabled = true, hour = 23))
        assertNull(select(enabled = true, hour = 3))
    }

    @Test
    fun `empty or wrong meal time text is skipped`() {
        val slot = AutoIsfSlots.select(true, 12 * 60) { if (it == Slot.LUNCH) "" else windows.getValue(it) }
        assertNull(slot)
    }

    @Test
    fun `every slot maps the same global keys and no slot key is shared`() {
        val globals = Slot.BREAKFAST.doubles.keys
        assertEquals(14, globals.size)
        Slot.entries.forEach { slot ->
            assertEquals(globals, slot.doubles.keys)
            assertEquals(setOf(IntKey.ApsAutoIsfIobThPercent), slot.ints.keys)
            slot.doubles.forEach { (global, slotKey) ->
                assertTrue(slotKey.key.startsWith("autoISF_slot_"), slotKey.key)
                assertEquals(global.defaultValue, slotKey.defaultValue, slotKey.key)
                assertEquals(global.min, slotKey.min, slotKey.key)
                assertEquals(global.max, slotKey.max, slotKey.key)
            }
        }
        val allSlotKeys: List<Any> = Slot.entries.flatMap { it.doubles.values.toList<Any>() + it.ints.values.toList<Any>() }
        assertEquals(allSlotKeys.size, allSlotKeys.toSet().size)
        assertTrue(DoubleKey.ApsAutoIsfMax !in allSlotKeys)
    }
}
