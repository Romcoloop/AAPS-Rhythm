package app.aaps.plugins.aps.openAPSAutoISF

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealWindowsTest {

    private val meals = listOf("06:30-09:00", "11:30-13:30", "17:30-20:00")

    @Test
    fun `parse valid window`() {
        assertEquals(MealWindows.Window(390, 540), MealWindows.parse("06:30-09:00"))
        assertEquals(MealWindows.Window(390, 540), MealWindows.parse(" 6:30 - 9:00 "))
    }

    @Test
    fun `parse wrong or empty text gives null`() {
        assertNull(MealWindows.parse(""))
        assertNull(MealWindows.parse("6:30"))
        assertNull(MealWindows.parse("25:00-26:00"))
        assertNull(MealWindows.parse("08:61-09:00"))
        assertNull(MealWindows.parse("08:00-08:00"))
    }

    @Test
    fun `no windows means no change`() {
        val r = MealWindows.calculate(listOf("", "", ""), minuteOfDay = 600, outsideFactor = 0.3)
        assertEquals(1.0, r.scale)
        assertEquals(0, r.windowCount)
    }

    @Test
    fun `inside window full weight`() {
        val r = MealWindows.calculate(meals, minuteOfDay = 12 * 60, outsideFactor = 0.3)
        assertTrue(r.inWindow)
        assertEquals(1.0, r.scale)
    }

    @Test
    fun `outside window uses factor`() {
        val r = MealWindows.calculate(meals, minuteOfDay = 15 * 60, outsideFactor = 0.3)
        assertFalse(r.inWindow)
        assertEquals(0.3, r.scale)
    }

    @Test
    fun `end time is not inside`() {
        assertFalse(MealWindows.calculate(meals, minuteOfDay = 9 * 60, outsideFactor = 0.5).inWindow)
        assertTrue(MealWindows.calculate(meals, minuteOfDay = 9 * 60 - 1, outsideFactor = 0.5).inWindow)
    }

    @Test
    fun `window over midnight`() {
        val w = listOf("22:00-01:00")
        assertTrue(MealWindows.calculate(w, 23 * 60, 0.5).inWindow)
        assertTrue(MealWindows.calculate(w, 30, 0.5).inWindow)
        assertFalse(MealWindows.calculate(w, 2 * 60, 0.5).inWindow)
    }

    @Test
    fun `factor is limited to 0 to 1`() {
        assertEquals(1.0, MealWindows.calculate(meals, 15 * 60, 3.0).scale)
        assertEquals(0.0, MealWindows.calculate(meals, 15 * 60, -1.0).scale)
    }
}
