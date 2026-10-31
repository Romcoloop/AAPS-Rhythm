package app.aaps.plugins.aps.keys

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.aps.ApsStrings

enum class ApsIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.URL,
    // urlRef rather than urlResId: a resource id is an Android Int and means nothing off Android.
    override val urlRef: TextRef? = null,
    override val confirmationMessage: TextRef? = null,
    override val dependency: BooleanPreferenceKey? = null,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    LinkToDocs(
        key = "aps_link_to_docs",
        title = ApsStrings.openapsama_link_to_preference_json_doc_txt,
        preferenceType = PreferenceType.URL,
        urlRef = ApsStrings.openapsama_link_to_preference_json_doc
    ),

    /** Copies the global AutoISF and SMB delivery values into all slots. The click handler is attached at runtime. */
    AutoIsfSlotsCopyGlobal(
        key = "autoisf_slots_copy_global",
        title = ApsStrings.autoisf_slots_copy_global,
        summary = ApsStrings.autoisf_slots_copy_global_summary,
        preferenceType = PreferenceType.CLICK,
        confirmationMessage = ApsStrings.autoisf_slots_copy_global_confirm
    ),

    /** Copies the weekday meal slot values into the weekend meal slots. The click handler is attached at runtime. */
    AutoIsfSlotsCopyWeekdayToWeekend(
        key = "autoisf_slots_copy_weekday_to_weekend",
        title = ApsStrings.autoisf_slots_copy_weekday_to_weekend,
        summary = ApsStrings.autoisf_slots_copy_weekday_to_weekend_summary,
        preferenceType = PreferenceType.CLICK,
        dependency = BooleanKey.ApsAutoIsfWeekendSlotsEnabled,
        visibility = ElementVisibility { it.preferences.get(BooleanKey.ApsAutoIsfSlotsEnabled) },
        confirmationMessage = ApsStrings.autoisf_slots_copy_weekday_to_weekend_confirm
    )
}
