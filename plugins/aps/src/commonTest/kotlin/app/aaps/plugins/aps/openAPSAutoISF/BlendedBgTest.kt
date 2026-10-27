package app.aaps.plugins.aps.openAPSAutoISF

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlendedBgTest {

    private val target = 100.0

    @Test
    fun `weight 0 keeps real BG`() {
        val r = BlendedBg.calculate(glucose = 120.0, a0 = 120.0, a1 = 10.0, a2 = 0.0, corrSqu = 0.99, targetBg = target, weight = 0.0)
        assertEquals(120.0, r.bg)
        assertEquals(0.0, r.lift)
        assertNull(r.predictedBg)
    }

    @Test
    fun `low correlation keeps real BG`() {
        val r = BlendedBg.calculate(120.0, 120.0, 10.0, 0.0, corrSqu = 0.85, targetBg = target, weight = 0.5)
        assertEquals(120.0, r.bg)
        assertEquals(0.0, r.lift)
    }

    @Test
    fun `below target keeps real BG`() {
        val r = BlendedBg.calculate(90.0, 90.0, 10.0, 0.0, 0.99, targetBg = target, weight = 0.5)
        assertEquals(90.0, r.bg)
        assertEquals(0.0, r.lift)
    }

    @Test
    fun `falling BG never lowers BG`() {
        val r = BlendedBg.calculate(150.0, 150.0, -8.0, 0.0, 0.99, target, 0.5)
        assertEquals(150.0, r.bg)
        assertEquals(0.0, r.lift)
    }

    @Test
    fun `rising BG is lifted by weight times expected rise`() {
        // +5 mg/dl per 5 min, 20 min ahead = +20 mg/dl, weight 0.5 = +10 mg/dl
        val r = BlendedBg.calculate(120.0, 120.0, 5.0, 0.0, 0.99, target, 0.5)
        assertEquals(140.0, r.predictedBg)
        assertEquals(10.0, r.lift)
        assertEquals(130.0, r.bg)
    }

    @Test
    fun `lift is capped`() {
        // +20 mg/dl per 5 min -> +80 in 20 min, 0.5 -> 40, capped to 36
        val r = BlendedBg.calculate(150.0, 150.0, 20.0, 0.0, 0.99, target, 0.5)
        assertEquals(BlendedBg.MAX_LIFT_MGDL, r.lift)
        assertEquals(150.0 + BlendedBg.MAX_LIFT_MGDL, r.bg)
    }

    @Test
    fun `weight is capped`() {
        val r = BlendedBg.calculate(120.0, 120.0, 5.0, 0.0, 0.99, target, 5.0)
        assertEquals(10.0, r.lift)
    }

    @Test
    fun `parabola that turns down uses value at horizon`() {
        // a1 = 6, a2 = -1 : at x = 4 -> 24 - 16 = +8 mg/dl, weight 0.5 -> +4
        val r = BlendedBg.calculate(130.0, 130.0, 6.0, -1.0, 0.99, target, 0.5)
        assertEquals(4.0, r.lift)
    }
}
