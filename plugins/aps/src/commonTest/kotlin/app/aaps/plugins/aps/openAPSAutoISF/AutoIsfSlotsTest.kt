package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots.DayType
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots.Slot
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoIsfSlotsTest {

    private val windows = mapOf(
        Slot.BREAKFAST to "05:30-08:00",
        Slot.LUNCH to "11:00-14:00",
        Slot.DINNER to "17:00-20:00",
        Slot.BREAKFAST_WEEKEND to "07:00-10:00",
        Slot.LUNCH_WEEKEND to "12:00-15:00",
        Slot.DINNER_WEEKEND to "18:00-21:00",
        Slot.NIGHT to "23:00-05:30"
    )

    private fun select(enabled: Boolean, hour: Int, minute: Int = 0, weekend: Boolean = false, texts: Map<Slot, String> = windows): Slot? =
        AutoIsfSlots.select(enabled, weekend, hour * 60 + minute) { texts.getValue(it) }

    @Test
    fun `slots off means no slot`() {
        assertNull(select(enabled = false, hour = 12))
        assertNull(select(enabled = false, hour = 12, weekend = true))
        assertNull(select(enabled = false, hour = 2))
    }

    @Test
    fun `weekday slot is chosen by the clock`() {
        assertEquals(Slot.BREAKFAST, select(enabled = true, hour = 5, minute = 30))
        assertEquals(Slot.BREAKFAST, select(enabled = true, hour = 7, minute = 59))
        assertEquals(Slot.LUNCH, select(enabled = true, hour = 12))
        assertEquals(Slot.DINNER, select(enabled = true, hour = 19, minute = 30))
    }

    @Test
    fun `weekend slot is chosen on a weekend day`() {
        assertEquals(Slot.BREAKFAST_WEEKEND, select(enabled = true, hour = 8, weekend = true))
        assertEquals(Slot.LUNCH_WEEKEND, select(enabled = true, hour = 12, weekend = true))
        assertEquals(Slot.DINNER_WEEKEND, select(enabled = true, hour = 20, minute = 30, weekend = true))
        // the weekday windows do not count on a weekend day: 05:30 is weekday breakfast, weekend breakfast starts at 07:00
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 5, minute = 15, weekend = true))
        assertNull(select(enabled = true, hour = 6, weekend = true))
        // and the weekend windows do not count on a weekday: 20:30 is weekend dinner only
        assertNull(select(enabled = true, hour = 20, minute = 30))
    }

    @Test
    fun `outside all windows means global values`() {
        assertNull(select(enabled = true, hour = 8))       // end is not included
        assertNull(select(enabled = true, hour = 15))
        assertNull(select(enabled = true, hour = 22))
        assertNull(select(enabled = true, hour = 15, weekend = true))
    }

    @Test
    fun `night slot goes over midnight, every day`() {
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 23))
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 2))
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 5, minute = 29))
        assertNull(select(enabled = true, hour = 22, minute = 59))
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 2, weekend = true))
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 23, weekend = true))
    }

    @Test
    fun `a meal wins over the night where they overlap`() {
        val texts = windows + (Slot.NIGHT to "22:00-06:00")
        assertEquals(Slot.BREAKFAST, select(enabled = true, hour = 5, minute = 45, texts = texts))
        assertEquals(Slot.NIGHT, select(enabled = true, hour = 5, minute = 15, texts = texts))
        assertEquals(Slot.BREAKFAST_WEEKEND, select(enabled = true, hour = 7, minute = 10, weekend = true, texts = texts + (Slot.BREAKFAST_WEEKEND to "07:00-10:00") + (Slot.NIGHT to "23:00-08:00")))
    }

    @Test
    fun `empty or wrong window text is skipped`() {
        val slot = AutoIsfSlots.select(true, false, 12 * 60) { if (it == Slot.LUNCH) "" else windows.getValue(it) }
        assertNull(slot)
        val night = AutoIsfSlots.select(true, false, 2 * 60) { if (it == Slot.NIGHT) "23:00-" else windows.getValue(it) }
        assertNull(night)
    }

    @Test
    fun `saturday and sunday are weekend days`() {
        assertFalse(AutoIsfSlots.isWeekendDay(LocalDate(2026, 10, 30)))   // Friday
        assertTrue(AutoIsfSlots.isWeekendDay(LocalDate(2026, 10, 31)))    // Saturday
        assertTrue(AutoIsfSlots.isWeekendDay(LocalDate(2026, 11, 1)))     // Sunday
        assertFalse(AutoIsfSlots.isWeekendDay(LocalDate(2026, 11, 2)))    // Monday
    }

    @Test
    fun `holiday is over the day after its end date`() {
        val today = LocalDate(2026, 12, 27)
        assertTrue(AutoIsfSlots.holidayOver("2026-12-26", today))
        assertFalse(AutoIsfSlots.holidayOver("2026-12-27", today))     // the end date itself is still holiday
        assertFalse(AutoIsfSlots.holidayOver("2027-01-02", today))
        assertFalse(AutoIsfSlots.holidayOver(" 2026-12-28 ", today))
        assertTrue(AutoIsfSlots.holidayOver(" 2026-12-20 ", today))
        // no date or a wrong date: holiday mode is switched off by hand only
        assertFalse(AutoIsfSlots.holidayOver("", today))
        assertFalse(AutoIsfSlots.holidayOver("26.12.2026", today))
        assertFalse(AutoIsfSlots.holidayOver("2026-13-01", today))
    }

    @Test
    fun `slot order puts the meals before the night and the weekday before the weekend`() {
        assertEquals(
            listOf(Slot.BREAKFAST, Slot.LUNCH, Slot.DINNER, Slot.BREAKFAST_WEEKEND, Slot.LUNCH_WEEKEND, Slot.DINNER_WEEKEND, Slot.NIGHT),
            Slot.entries
        )
        assertEquals(listOf(DayType.WEEKDAY, DayType.WEEKDAY, DayType.WEEKDAY, DayType.WEEKEND, DayType.WEEKEND, DayType.WEEKEND, DayType.EVERY_DAY), Slot.entries.map { it.dayType })
        assertEquals(Slot.BREAKFAST, Slot.BREAKFAST_WEEKEND.weekdayTwin)
        assertEquals(Slot.LUNCH, Slot.LUNCH_WEEKEND.weekdayTwin)
        assertEquals(Slot.DINNER, Slot.DINNER_WEEKEND.weekdayTwin)
        assertNull(Slot.NIGHT.weekdayTwin)
        assertNull(Slot.BREAKFAST.weekdayTwin)
        assertEquals("Breakfast-weekend", Slot.BREAKFAST_WEEKEND.label)
        assertEquals("Night", Slot.NIGHT.label)
    }

    @Test
    fun `every slot maps the same global keys and no slot key is shared`() {
        val globals = Slot.BREAKFAST.doubles.keys
        assertEquals(14, globals.size)
        assertEquals(7, Slot.entries.size)
        Slot.entries.forEach { slot ->
            assertEquals(globals, slot.doubles.keys)
            assertEquals(setOf(IntKey.ApsAutoIsfIobThPercent), slot.ints.keys)
            slot.doubles.forEach { (global, slotKey) ->
                assertTrue(slotKey.key.startsWith("autoISF_slot_"), slotKey.key)
                assertEquals(global.defaultValue, slotKey.defaultValue, slotKey.key)
                assertEquals(global.min, slotKey.min, slotKey.key)
                assertEquals(global.max, slotKey.max, slotKey.key)
            }
            val windowKeys = Slot.entries.map { it.windowKey }
            assertEquals(windowKeys.size, windowKeys.toSet().size)
        }
        val allSlotKeys: List<Any> = Slot.entries.flatMap { it.doubles.values.toList<Any>() + it.ints.values.toList<Any>() }
        assertEquals(allSlotKeys.size, allSlotKeys.toSet().size)
        assertTrue(DoubleKey.ApsAutoIsfMax !in allSlotKeys)
    }
}
