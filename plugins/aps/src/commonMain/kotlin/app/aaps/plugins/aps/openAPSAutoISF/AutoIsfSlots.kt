package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber

/**
 * Slots for AutoISF: each slot has its own copy of the AutoISF and SMB delivery values.
 *
 * There are seven slots: breakfast, lunch and dinner for a weekday, the same three for the
 * weekend, and one night slot for every day. A slot is a time window ("HH:MM-HH:MM", see
 * [MealWindows]) plus a day type:
 * - the weekday meal slots are used from Monday to Friday,
 * - the weekend meal slots on Saturday and Sunday, and on a weekday while holiday mode
 *   ([BooleanKey.ApsAutoIsfHolidayMode]) is on; with [BooleanKey.ApsAutoIsfWeekendSlotsEnabled]
 *   off, the weekday slots are used every day,
 * - the night slot every day; its window may go over midnight.
 *
 * The slot is chosen by the clock only. Where a meal window and the night window overlap, the
 * meal slot wins; between meal windows the first one in [Slot] order wins. Outside all windows,
 * or while [BooleanKey.ApsAutoIsfSlotsEnabled] is off, the global values are used, so the
 * switch off means "exactly as before".
 *
 * Only numbers are per slot. The on/off switches of AutoISF stay global, so the slots can be
 * compared with each other.
 */
class AutoIsfSlots(
    private val preferences: Preferences,
    private val now: () -> LocalDateTime
) {

    /** On which days a slot is used. */
    enum class DayType { WEEKDAY, WEEKEND, EVERY_DAY }

    /** What kind of day the run is on, for the log. [DayKind.WEEKEND] and [DayKind.HOLIDAY] both use the weekend slots. */
    enum class DayKind(val label: String) { WEEKDAY("weekday"), WEEKEND("weekend"), HOLIDAY("holiday") }

    enum class Slot(
        val dayType: DayType,
        val windowKey: StringKey,
        val doubles: Map<DoubleKey, DoubleKey>,
        val ints: Map<IntKey, IntKey>
    ) {

        BREAKFAST(
            dayType = DayType.WEEKDAY,
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
            dayType = DayType.WEEKDAY,
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
            dayType = DayType.WEEKDAY,
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
        ),
        BREAKFAST_WEEKEND(
            dayType = DayType.WEEKEND,
            windowKey = StringKey.ApsAutoIsfMealWindowBreakfastWeekend,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotBreakfastWeekendMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotBreakfastWeekendMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotBreakfastWeekendDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotBreakfastWeekendSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotBreakfastWeekendSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotBreakfastWeekendSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotBreakfastWeekendSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotBreakfastWeekendSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotBreakfastWeekendIobThPercent
            )
        ),
        LUNCH_WEEKEND(
            dayType = DayType.WEEKEND,
            windowKey = StringKey.ApsAutoIsfMealWindowLunchWeekend,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotLunchWeekendMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotLunchWeekendMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotLunchWeekendDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotLunchWeekendSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotLunchWeekendSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotLunchWeekendSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotLunchWeekendSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotLunchWeekendSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotLunchWeekendIobThPercent
            )
        ),
        DINNER_WEEKEND(
            dayType = DayType.WEEKEND,
            windowKey = StringKey.ApsAutoIsfMealWindowDinnerWeekend,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotDinnerWeekendMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotDinnerWeekendMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotDinnerWeekendDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotDinnerWeekendSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotDinnerWeekendSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotDinnerWeekendSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotDinnerWeekendSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotDinnerWeekendSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotDinnerWeekendIobThPercent
            )
        ),
        NIGHT(
            dayType = DayType.EVERY_DAY,
            windowKey = StringKey.ApsAutoIsfMealWindowNight,
            doubles = mapOf(
                DoubleKey.ApsAutoIsfMin to DoubleKey.ApsAutoIsfSlotNightMin,
                DoubleKey.ApsAutoIsfMax to DoubleKey.ApsAutoIsfSlotNightMax,
                DoubleKey.ApsAutoIsfBgAccelWeight to DoubleKey.ApsAutoIsfSlotNightBgAccelWeight,
                DoubleKey.ApsAutoIsfBgBrakeWeight to DoubleKey.ApsAutoIsfSlotNightBgBrakeWeight,
                DoubleKey.ApsAutoIsfLowBgWeight to DoubleKey.ApsAutoIsfSlotNightLowBgWeight,
                DoubleKey.ApsAutoIsfHighBgWeight to DoubleKey.ApsAutoIsfSlotNightHighBgWeight,
                DoubleKey.ApsAutoIsfPpWeight to DoubleKey.ApsAutoIsfSlotNightPpWeight,
                DoubleKey.ApsAutoIsfBgBlendWeight to DoubleKey.ApsAutoIsfSlotNightBgBlendWeight,
                DoubleKey.ApsAutoIsfDuraWeight to DoubleKey.ApsAutoIsfSlotNightDuraWeight,
                DoubleKey.ApsAutoIsfSmbDeliveryRatio to DoubleKey.ApsAutoIsfSlotNightSmbDeliveryRatio,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to DoubleKey.ApsAutoIsfSlotNightSmbDeliveryRatioMin,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to DoubleKey.ApsAutoIsfSlotNightSmbDeliveryRatioMax,
                DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to DoubleKey.ApsAutoIsfSlotNightSmbDeliveryRatioBgRange,
                DoubleKey.ApsAutoIsfSmbMaxRangeExtension to DoubleKey.ApsAutoIsfSlotNightSmbMaxRangeExtension
            ),
            ints = mapOf(
                IntKey.ApsAutoIsfIobThPercent to IntKey.ApsAutoIsfSlotNightIobThPercent
            )
        );

        /** True for a meal slot (weekday or weekend), false for the night. */
        val isMeal: Boolean get() = dayType != DayType.EVERY_DAY

        /** Short name for the log, one word: "Breakfast", "Breakfast-weekend", "Night". */
        val label: String get() = name.lowercase().replace('_', '-').replaceFirstChar { it.uppercase() }

        /** The weekday slot a weekend slot is copied from, null for the others. */
        val weekdayTwin: Slot?
            get() = when (this) {
                BREAKFAST_WEEKEND -> BREAKFAST
                LUNCH_WEEKEND     -> LUNCH
                DINNER_WEEKEND    -> DINNER
                else              -> null
            }
    }

    val enabled: Boolean get() = preferences.get(BooleanKey.ApsAutoIsfSlotsEnabled)

    /** Own slots on the weekend and in holiday mode. Read only while [enabled]. */
    private val weekendEnabled: Boolean get() = preferences.get(BooleanKey.ApsAutoIsfWeekendSlotsEnabled)

    private var runStarted = false
    private var runMinute = 0
    private var runDay = DayKind.WEEKDAY
    private var runSlot: Slot? = null

    /**
     * Fixes the clock, the kind of day and the slot for one loop run. Call it once at the start of
     * the run, before any value is read. All reads until the next call then use the same slot, so a
     * run that crosses a window boundary cannot mix slot and global values.
     *
     * This is also where holiday mode switches itself off once its end date has passed.
     */
    fun beginRun(): Slot? {
        val time = now()
        endHolidayIfOver(time.date)
        runMinute = minuteOf(time)
        runDay = dayKind(time)
        runSlot = select(enabled, runDay != DayKind.WEEKDAY, runMinute) { preferences.get(it.windowKey) }
        runStarted = true
        return runSlot
    }

    /** Minutes since midnight as fixed by [beginRun], or the clock now when no run was started. */
    val minuteOfRun: Int get() = if (runStarted) runMinute else minuteOf(now())

    /** Kind of day as fixed by [beginRun], or from the clock now when no run was started. */
    val dayOfRun: DayKind get() = if (runStarted) runDay else dayKind(now())

    /**
     * The slot of the current run (see [beginRun]), or null when the slots are off or the run is
     * outside all windows. Before the first run it is read from the clock.
     */
    fun activeSlot(): Slot? =
        if (runStarted) runSlot
        else now().let { time -> select(enabled, dayKind(time) != DayKind.WEEKDAY, minuteOf(time)) { preferences.get(it.windowKey) } }

    /** The window text of a slot, for the log. */
    fun windowText(slot: Slot): String = preferences.get(slot.windowKey)

    /**
     * The meal times of the current kind of day, for the "meal factors outside meal times" scale.
     * Weekday times while the slots or the weekend slots are off, so the switch off changes nothing.
     */
    fun mealWindowTexts(): List<String> {
        val wanted = if (dayOfRun == DayKind.WEEKDAY) DayType.WEEKDAY else DayType.WEEKEND
        return Slot.entries.filter { it.dayType == wanted }.map { preferences.get(it.windowKey) }
    }

    /** Value of [key] for now: the slot copy inside a window, the global value otherwise. */
    fun get(key: DoubleKey): Double {
        val slotKey = activeSlot()?.doubles?.get(key) ?: key
        return preferences.get(slotKey)
    }

    /** Value of [key] for now: the slot copy inside a window, the global value otherwise. */
    fun get(key: IntKey): Int {
        val slotKey = activeSlot()?.ints?.get(key) ?: key
        return preferences.get(slotKey)
    }

    private fun minuteOf(time: LocalDateTime): Int = time.hour * 60 + time.minute

    /** Weekday while the slots or the weekend slots are off; else holiday mode first, then the calendar. */
    private fun dayKind(time: LocalDateTime): DayKind = when {
        !enabled || !weekendEnabled                            -> DayKind.WEEKDAY
        preferences.get(BooleanKey.ApsAutoIsfHolidayMode)      -> DayKind.HOLIDAY
        isWeekendDay(time.date)                                -> DayKind.WEEKEND
        else                                                   -> DayKind.WEEKDAY
    }

    /** Switches holiday mode off, and clears its end date, once the end date is in the past. */
    private fun endHolidayIfOver(today: LocalDate) {
        if (!preferences.get(BooleanKey.ApsAutoIsfHolidayMode)) return
        if (!holidayOver(preferences.get(StringKey.ApsAutoIsfHolidayUntil), today)) return
        preferences.put(BooleanKey.ApsAutoIsfHolidayMode, false)
        preferences.put(StringKey.ApsAutoIsfHolidayUntil, "")
    }

    companion object {

        /** Saturday or Sunday. */
        fun isWeekendDay(date: LocalDate): Boolean = date.dayOfWeek.isoDayNumber >= 6

        /**
         * True when the holiday end date ("YYYY-MM-DD") is before [today]. An empty or wrong text
         * never ends the holiday: then it is switched off by hand.
         */
        fun holidayOver(until: String, today: LocalDate): Boolean {
            val end = runCatching { LocalDate.parse(until.trim()) }.getOrNull() ?: return false
            return today > end
        }

        /**
         * Pure part of [activeSlot], so it can be tested without preferences.
         *
         * @param weekend true when the weekend meal slots are wanted (weekend day or holiday mode)
         * @param windowText the window text ("HH:MM-HH:MM") of a slot
         */
        fun select(enabled: Boolean, weekend: Boolean, minuteOfDay: Int, windowText: (Slot) -> String): Slot? {
            if (!enabled) return null
            val meals = if (weekend) DayType.WEEKEND else DayType.WEEKDAY
            return Slot.entries.firstOrNull { slot ->
                (slot.dayType == meals || slot.dayType == DayType.EVERY_DAY) &&
                    MealWindows.parse(windowText(slot))?.contains(minuteOfDay) == true
            }
        }
    }

    /** Writes the global values into all slots. Values already in the slots are overwritten. Windows are not touched. */
    fun copyGlobalToAllSlots() {
        Slot.entries.forEach { slot ->
            slot.doubles.forEach { (global, slotKey) -> preferences.put(slotKey, preferences.get(global)) }
            slot.ints.forEach { (global, slotKey) -> preferences.put(slotKey, preferences.get(global)) }
        }
    }

    /** Writes the values of the weekday meal slots into the weekend meal slots. Windows are not touched. */
    fun copyWeekdayToWeekend() {
        Slot.entries.forEach { slot ->
            val from = slot.weekdayTwin ?: return@forEach
            slot.doubles.forEach { (global, slotKey) -> preferences.put(slotKey, preferences.get(from.doubles.getValue(global))) }
            slot.ints.forEach { (global, slotKey) -> preferences.put(slotKey, preferences.get(from.ints.getValue(global))) }
        }
    }
}
