package app.aaps.plugins.aps.keys

import app.aaps.core.keys.PreferenceType
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
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    LinkToDocs(
        key = "aps_link_to_docs",
        title = ApsStrings.openapsama_link_to_preference_json_doc_txt,
        preferenceType = PreferenceType.URL,
        urlRef = ApsStrings.openapsama_link_to_preference_json_doc
    ),

    /** Copies the global AutoISF and SMB delivery values into the three meal slots. The click handler is attached at runtime. */
    AutoIsfSlotsCopyGlobal(
        key = "autoisf_slots_copy_global",
        title = ApsStrings.autoisf_slots_copy_global,
        summary = ApsStrings.autoisf_slots_copy_global_summary,
        preferenceType = PreferenceType.CLICK,
        confirmationMessage = ApsStrings.autoisf_slots_copy_global_confirm
    )
}
