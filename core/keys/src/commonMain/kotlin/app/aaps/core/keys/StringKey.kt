package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class StringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesRefs: Map<String, TextRef> = emptyMap(),
    /**
     * Entry labels that are not a resource in this module. Only the units preference needs this:
     * "mg/dL" and "mmol/L" read the same in every language, and the `units_*` strings themselves
     * now live in `:core:ui`, which this module cannot depend on.
     */
    private val entriesLiterals: Map<String, String> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val isHashed: Boolean = false,
    override val validator: StringValidator = StringValidator.NONE,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val sync: SyncSpec? = null
) : StringPreferenceKey {

    GeneralUnits(
        key = "units",
        defaultValue = "mg/dl",
        title = KeysStrings.pref_title_units,
        preferenceType = PreferenceType.LIST,
        entriesLiterals = mapOf(
            "mg/dl" to "mg/dL",
            "mmol" to "mmol/L"
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    GeneralLanguage(
        key = "language",
        defaultValue = "default",
        title = KeysStrings.pref_title_language,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "default" to KeysStrings.lang_default,
            "en" to KeysStrings.lang_en,
            "af" to KeysStrings.lang_af,
            "bg" to KeysStrings.lang_bg,
            "cs" to KeysStrings.lang_cs,
            "de" to KeysStrings.lang_de,
            "dk" to KeysStrings.lang_dk,
            "fr" to KeysStrings.lang_fr,
            "nl" to KeysStrings.lang_nl,
            "es" to KeysStrings.lang_es,
            "el" to KeysStrings.lang_el,
            "ga" to KeysStrings.lang_ga,
            "it" to KeysStrings.lang_it,
            "ko" to KeysStrings.lang_ko,
            "lt" to KeysStrings.lang_lt,
            "nb" to KeysStrings.lang_nb,
            "pl" to KeysStrings.lang_pl,
            "pt" to KeysStrings.lang_pt,
            "pt_BR" to KeysStrings.lang_pt_br,
            "ro" to KeysStrings.lang_ro,
            "ru" to KeysStrings.lang_ru,
            "sk" to KeysStrings.lang_sk,
            "sv" to KeysStrings.lang_sv,
            "tr" to KeysStrings.lang_tr,
            "zh_TW" to KeysStrings.lang_zh_tw,
            "zh_CN" to KeysStrings.lang_zh_cn
        ),
        defaultedBySM = true,
        // Android 12 has no app languages: AAPS follows the phone there
        visibility = ElementVisibility { it.supportsAppLanguage }
    ),
    // Not the patient: the person using THIS phone. It names this AAPS instance (export file names,
    // the OTP label, the watch), so it stays device-local and carries no SyncSpec - a client must not
    // take the master's name. The stored key keeps its old "patient_name" spelling on purpose, so no
    // installation loses its value.
    GeneralInstanceName(
        key = "patient_name",
        defaultValue = "",
        title = KeysStrings.pref_title_patient_name,
        summary = KeysStrings.pref_summary_patient_name,
        validator = StringValidator.personName()
    ),
    GeneralDarkMode(
        key = "use_dark_mode",
        defaultValue = "dark",
        title = KeysStrings.pref_title_app_color_scheme,
        summary = KeysStrings.pref_summary_theme_switcher,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "dark" to KeysStrings.pref_dark_theme,
            "light" to KeysStrings.pref_light_theme,
            "system" to KeysStrings.pref_follow_system_theme
        ),
        defaultedBySM = true
    ),

    AapsDirectoryUri(key = "aaps_directory", defaultValue = "", title = KeysStrings.pref_title_aaps_directory),

    ProtectionMasterPassword(key = "master_password", defaultValue = "", title = KeysStrings.pref_title_master_password, isPassword = true, isHashed = true),
    ProtectionSettingsPassword(
        key = "settings_password", defaultValue = "", title = KeysStrings.pref_title_settings_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionSettingsPin(
        key = "settings_pin", defaultValue = "", title = KeysStrings.pref_title_settings_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionApplicationPassword(
        key = "application_password", defaultValue = "", title = KeysStrings.pref_title_application_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionApplicationPin(
        key = "application_pin", defaultValue = "", title = KeysStrings.pref_title_application_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionBolusPassword(
        key = "bolus_password", defaultValue = "", title = KeysStrings.pref_title_bolus_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionBolusPin(
        key = "bolus_pin", defaultValue = "", title = KeysStrings.pref_title_bolus_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PIN.ordinal)
    ),

    ApsAutoIsfMealWindowBreakfast(
        key = "autoISF_meal_window_breakfast",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_breakfast,
        summary = KeysStrings.pref_summary_autoisf_meal_window,
        defaultedBySM = true,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 06:30-09:00"),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfMealWindowLunch(
        key = "autoISF_meal_window_lunch",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_lunch,
        summary = KeysStrings.pref_summary_autoisf_meal_window,
        defaultedBySM = true,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 06:30-09:00"),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfMealWindowDinner(
        key = "autoISF_meal_window_dinner",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_dinner,
        summary = KeysStrings.pref_summary_autoisf_meal_window,
        defaultedBySM = true,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 06:30-09:00"),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    // AutoISF meal slots, weekend: the meal times used on Saturday, Sunday and in holiday mode.
    ApsAutoIsfMealWindowBreakfastWeekend(
        key = "autoISF_meal_window_breakfast_we",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_breakfast_weekend,
        summary = KeysStrings.pref_summary_autoisf_meal_window_weekend,
        defaultedBySM = true,
        dependency = BooleanKey.ApsAutoIsfWeekendSlotsEnabled,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 07:00-10:00"),
        visibility = AutoIsfVisibility.WEEKEND_SLOTS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfMealWindowLunchWeekend(
        key = "autoISF_meal_window_lunch_we",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_lunch_weekend,
        summary = KeysStrings.pref_summary_autoisf_meal_window_weekend,
        defaultedBySM = true,
        dependency = BooleanKey.ApsAutoIsfWeekendSlotsEnabled,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 12:00-15:00"),
        visibility = AutoIsfVisibility.WEEKEND_SLOTS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfMealWindowDinnerWeekend(
        key = "autoISF_meal_window_dinner_we",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_dinner_weekend,
        summary = KeysStrings.pref_summary_autoisf_meal_window_weekend,
        defaultedBySM = true,
        dependency = BooleanKey.ApsAutoIsfWeekendSlotsEnabled,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 18:00-21:00"),
        visibility = AutoIsfVisibility.WEEKEND_SLOTS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    // AutoISF night slot: one window for every day, it may go over midnight.
    ApsAutoIsfMealWindowNight(
        key = "autoISF_meal_window_night",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_meal_window_night,
        summary = KeysStrings.pref_summary_autoisf_meal_window_night,
        defaultedBySM = true,
        dependency = BooleanKey.ApsAutoIsfSlotsEnabled,
        validator = StringValidator.regex("^\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*-\\s*([01]?[0-9]|2[0-3]):[0-5][0-9]\\s*$", "Use HH:MM-HH:MM, for example 23:00-05:30"),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    /** Last day of holiday mode as YYYY-MM-DD. After that day [BooleanKey.ApsAutoIsfHolidayMode] switches itself off. Empty = switch it off by hand. */
    ApsAutoIsfHolidayUntil(
        key = "autoISF_holiday_until",
        defaultValue = "",
        title = KeysStrings.pref_title_autoisf_holiday_until,
        summary = KeysStrings.pref_summary_autoisf_holiday_until,
        defaultedBySM = true,
        dependency = BooleanKey.ApsAutoIsfHolidayMode,
        validator = StringValidator.regex("^\\s*\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])\\s*$", "Use YYYY-MM-DD, for example 2026-12-26, or leave it empty"),
        visibility = AutoIsfVisibility.WEEKEND_SLOTS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    SafetyAge(key = "age", defaultValue = "adult", title = KeysStrings.pref_title_patient_age, preferenceType = PreferenceType.LIST, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    MaintenanceEmail(
        key = "maintenance_logs_email",
        defaultValue = "logs@aaps.app",
        title = KeysStrings.maintenance_email,
        defaultedBySM = true,
        validator = StringValidator.email()
    ),
    MaintenanceIdentification(key = "email_for_crash_report", defaultValue = "", title = KeysStrings.pref_title_identification),
    AutomationLocation(
        key = "location",
        defaultValue = "PASSIVE",
        title = KeysStrings.pref_title_automation_location,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "PASSIVE" to KeysStrings.automation_location_passive,
            "NETWORK" to KeysStrings.automation_location_network,
            "GPS" to KeysStrings.automation_location_gps
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    SmsAllowedNumbers(
        key = "smscommunicator_allowednumbers",
        defaultValue = "",
        title = KeysStrings.smscommunicator_allowednumbers,
        summary = KeysStrings.smscommunicator_allowednumbers_summary,
        validator = StringValidator.multiPhone()
    ),
    SmsOtpPassword(
        key = "smscommunicator_otp_password",
        defaultValue = "",
        title = KeysStrings.smscommunicator_otp_pin,
        summary = KeysStrings.smscommunicator_otp_pin_summary,
        dependency = BooleanKey.SmsAllowRemoteCommands,
        isPassword = true,
        validator = StringValidator.pinStrength()
    ),

    VirtualPumpType(key = "virtualpump_type", defaultValue = "Generic AAPS", title = KeysStrings.pref_title_virtual_pump_type, preferenceType = PreferenceType.LIST),

    /**
     * Which embedded Watch Face Format face the wear app installs through Watch Face Push on a
     * Wear OS 6+ watch. Watch Face Push gives an app one slot, so this is a choice, not a set.
     * Values in [PushedWatchfaceId]. Older watches receive and keep the value but cannot act on it.
     *
     * Not in any preference screen: the Wear plugin's main page shows it as a card, and only once
     * the watch has reported that it has Watch Face Push. No summary for that reason.
     */
    WearPushedWatchface(
        key = "wear_pushed_watchface",
        defaultValue = PushedWatchfaceId.CWF,
        title = KeysStrings.pref_title_wear_pushed_watchface,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            PushedWatchfaceId.WFS to KeysStrings.wear_pushed_watchface_wfs,
            PushedWatchfaceId.CWF to KeysStrings.wear_pushed_watchface_cwf
        )
    ),

    NsClientUrl(
        key = "nsclientinternal_url",
        defaultValue = "",
        title = KeysStrings.ns_client_url_title,
        summary = KeysStrings.ns_client_url_summary,
        validator = StringValidator.httpsUrl()
    ),
    NsClientApiSecret(
        key = "nsclientinternal_api_secret",
        defaultValue = "",
        title = KeysStrings.ns_client_secret_title,
        summary = KeysStrings.ns_client_secret_summary,
        isPassword = true,
        validator = StringValidator.minLength(12)
    ),
    NsClientWifiSsids(
        key = "ns_wifi_ssids",
        defaultValue = "",
        title = KeysStrings.ns_wifi_ssids,
        summary = KeysStrings.ns_wifi_ssids_summary,
        dependency = BooleanKey.NsClientUseWifi
    ),
    NsClientAccessToken(
        key = "nsclient_token",
        defaultValue = "",
        title = KeysStrings.nsclient_token_title,
        summary = KeysStrings.nsclient_token_summary,
        isPassword = true,
        validator = StringValidator.minLength(17)
    ),

    ;

    override val entries: Map<String, TextRef> =
        entriesRefs + entriesLiterals.mapValues { TextRef.Literal(it.value) }
}
