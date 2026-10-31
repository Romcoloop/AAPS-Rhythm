package app.aaps.core.keys

import app.aaps.core.keys.interfaces.ElementVisibility

/**
 * Visibility rules shared by the AutoISF meal slot keys.
 *
 * A key can name only one [app.aaps.core.keys.interfaces.PreferenceKey.dependency]. The weekend
 * slot keys need two switches: the meal slots must be on and the weekend slots must be on. The
 * second switch is their dependency, this rule covers the first one.
 */
internal object AutoIsfVisibility {

    /** Shown only while both "Use meal slot settings" and "Separate weekend slots" are on. */
    val WEEKEND_SLOTS: ElementVisibility = ElementVisibility {
        it.preferences.get(BooleanKey.ApsAutoIsfSlotsEnabled) && it.preferences.get(BooleanKey.ApsAutoIsfWeekendSlotsEnabled)
    }
}
