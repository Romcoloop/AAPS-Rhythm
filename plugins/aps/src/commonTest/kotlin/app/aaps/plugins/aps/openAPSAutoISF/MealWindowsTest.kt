package app.aaps.plugins.aps.openAPSAutoISF

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealWindowsTest {

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
    fun `end time is not inside`() {
        val w = MealWindows.parse("06:30-09:00")!!
        assertFalse(w.contains(9 * 60))
        assertTrue(w.contains(9 * 60 - 1))
        assertTrue(w.contains(6 * 60 + 30))
        assertFalse(w.contains(6 * 60 + 29))
    }

    @Test
    fun `window over midnight`() {
        val w = MealWindows.parse("22:00-01:00")!!
        assertTrue(w.contains(23 * 60))
        assertTrue(w.contains(30))
        assertFalse(w.contains(2 * 60))
    }
}
