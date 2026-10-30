package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Meal slots for AutoISF: breakfast, lunch and dinner each have their own copy of the
 * AutoISF and SMB delivery values.
 *
 * The slot is chosen by the clock only. Inside the meal time of a slot (see [MealWindows] and
 * the `autoISF_meal_window_*` preferences) the values of that slot are used. Outside all meal
 * times, or while [BooleanKey.ApsAutoIsfSlotsEnabled] is off, the global values are used, so
 * the switch off means "exactly as before".
 *
 * Only numbers are per slot. The on/off switches of AutoISF stay global, so the slots can be
 * compared with each other.
 */
class AutoIsfSlots(
    private val preferences: Preferences,
    private val minuteOfDay: () -> Int
) {

    enum class Slot(
        val windowKey: StringKey,
        val doubles: Map<DoubleKey, DoubleKey>,
        val ints: Map<IntKey, IntKey>
    ) {

        BREAKFAST(
            windowKey = StringKey.ApsAutoIsfMealWindowBreakfast,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotBreakfastMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotBreakfastMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotBreakfastBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotBreakfastBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotBreakfastLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotBreakfastHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotBreakfastPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotBreakfastBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotBreakfastDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotBreakfastSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotBreakfastSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotBreakfastSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotBreakfastSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotBreakfastSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotBreakfastIobThPercent
            )
        ),
        LUNCH(
            windowKey = StringKey.ApsAutoIsfMealWindowLunch,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotLunchMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotLunchMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotLunchBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotLunchBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotLunchLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotLunchHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotLunchPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotLunchBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotLunchDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotLunchSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotLunchSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotLunchSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotLunchSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotLunchSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotLunchIobThPercent
            )
        ),
        DINNER(
            windowKey = StringKey.ApsAutoIsfMealWindowDinner,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotDinnerMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotDinnerMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotDinnerBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotDinnerBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotDinnerLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotDinnerHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotDinnerPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotDinnerBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotDinnerDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotDinnerSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotDinnerSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotDinnerSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotDinnerSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotDinnerSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotDinnerIobThPercent
            )
        );

        /** Short name for the log, for example "Breakfast". */
        val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
    }

    val enabled: Boolean get() = preferences.get(BooleanKey.ApsAutoIsfSlotsEnabled)

    /**
     * The slot whose meal time contains now, or null when the slots are off or now is outside
     * all meal times. When two meal times overlap, the first one in [Slot] order wins.
     */
    fun activeSlot(): Slot? = select(enabled, minuteOfDay()) { preferences.get(it.windowKey) }

    /** The meal time text of a slot, for the log. */
    fun windowText(slot: Slot): String = preferences.get(slot.windowKey)

    /** Value of [key] for now: the slot copy inside a meal time, the global value otherwise. */
    fun get(key: DoubleKey): Double {
        val slotKey = activeSlot()?.doubles?.get(key) ?: key
        return preferences.get(slotKey)
    }

    /** Value of [key] for now: the slot copy inside a meal time, the global value otherwise. */
    fun get(key: IntKey): Int {
        val slotKey = activeSlot()?.ints?.get(key) ?: key
        return preferences.get(slotKey)
    }

    companion object {

        /**
         * Pure part of [activeSlot], so it can be tested without preferences.
         *
         * @param windowText the meal time text ("HH:MM-HH:MM") of a slot
         */
        fun select(enabled: Boolean, minuteOfDay: Int, windowText: (Slot) -> String): Slot? {
            if (!enabled) return null
            return Slot.entries.firstOrNull { slot ->
                MealWindows.parse(windowText(slot))?.contains(minuteOfDay) == true
            }
        }
    }

    /** Writes the global values into all three slots. Values already in the slots are overwritten. */
    fun copyGlobalToAllSlots() {
        Slot.entries.forEach { slot ->
            slot.doubles.forEach { (global, slotKey) -> preferences.put(slotKey, preferences.get(global)) }
            slot.ints.forEach { (global, slotKey) -> preferences.put(slotKey, preferences.get(global)) }
        }
    }
}
