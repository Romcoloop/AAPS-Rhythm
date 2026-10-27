package app.aaps.plugins.aps.openAPSAutoISF

import kotlin.math.min

/**
 * Blended BG for bg_ISF (idea taken from Boost).
 *
 * bg_ISF normally looks only at the current BG. When BG rises fast, bg_ISF reacts late.
 * Here we look a little into the future with the parabola fit that AutoISF already has,
 * and move the BG used for bg_ISF up by a part of the expected rise.
 *
 * Safety rules:
 * - weight 0.0 (default) switches the feature off,
 * - only used when the parabola fit is good (correlation >= [MIN_CORRELATION]),
 * - only used at or above target,
 * - BG can only go up, never down (falling BG always uses the real BG),
 * - the lift is capped at [MAX_LIFT_MGDL].
 *
 * The final ISF is still limited by autoISF_min / autoISF_max in `withinISFlimits`.
 */
object BlendedBg {

    /** How far we look ahead with the parabola. Short, because the parabola is only good for a short time. */
    const val HORIZON_MINUTES = 20.0

    /** Max lift of BG for bg_ISF, in mg/dl (36 mg/dl = 2 mmol/l). */
    const val MAX_LIFT_MGDL = 36.0

    /** Same limit that acce_ISF uses. */
    const val MIN_CORRELATION = 0.9

    /** Max allowed weight. Also used as max of the preference. */
    const val MAX_WEIGHT = 0.5

    data class Result(
        /** BG that should be used for bg_ISF, mg/dl. */
        val bg: Double,
        /** BG predicted by the parabola at [HORIZON_MINUTES], mg/dl. Null when not used. */
        val predictedBg: Double?,
        /** How much [bg] is above the real BG, mg/dl. 0.0 when not used. */
        val lift: Double,
        /** Short reason for the log. */
        val reason: String
    )

    /**
     * @param glucose current BG, mg/dl
     * @param a0 parabola fit a0, mg/dl
     * @param a1 parabola fit a1, mg/dl per 5 min
     * @param a2 parabola fit a2, mg/dl per (5 min)^2
     * @param corrSqu parabola fit correlation (0..1)
     * @param targetBg current target, mg/dl
     * @param weight part of the expected rise that is used (0.0 = off)
     */
    fun calculate(
        glucose: Double,
        a0: Double,
        a1: Double,
        a2: Double,
        corrSqu: Double,
        targetBg: Double,
        weight: Double
    ): Result {
        if (weight <= 0.0) return Result(glucose, null, 0.0, "off")
        if (corrSqu < MIN_CORRELATION) return Result(glucose, null, 0.0, "fit correlation too low")
        if (glucose < targetBg) return Result(glucose, null, 0.0, "BG below target")

        // parabola time unit is 5 min, x = 0 is the last BG value
        val x = HORIZON_MINUTES / 5.0
        val predicted = a0 + a1 * x + a2 * x * x
        val expectedRise = predicted - a0
        if (expectedRise <= 0.0) return Result(glucose, predicted, 0.0, "no rise expected")

        val usedWeight = min(weight, MAX_WEIGHT)
        val lift = min(usedWeight * expectedRise, MAX_LIFT_MGDL)
        return Result(glucose + lift, predicted, lift, "rise expected")
    }
}
