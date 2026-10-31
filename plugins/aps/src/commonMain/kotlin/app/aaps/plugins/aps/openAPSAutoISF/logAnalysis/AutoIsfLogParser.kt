package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

/**
 * One AutoISF loop run, read back from the AAPS log.
 *
 * All glucose values are in mg/dl, as the algorithm logs them. Values that the log line did not
 * contain are null.
 */
data class AutoIsfLogRun(
    /** Time of the run, from `RT.timestamp`. The log line itself has no date. */
    val timestamp: Long,
    /** Time of the BG reading the run used. Several runs can use the same reading. */
    val bgTimestamp: Long?,
    val bg: Double,
    val eventualBg: Double?,
    val targetBg: Double?,
    val insulinReq: Double?,
    /** Requested SMB in U, 0.0 when the run gave no SMB. */
    val smbUnits: Double,
    val iob: Double?,
    val finalIsfFactor: Double?,
    val acceIsf: Double?,
    val bgIsf: Double?,
    val ppIsf: Double?,
    val duraIsf: Double?,
    /** The strongest factor was cut down to `autoISF_max`. */
    val limitedByAutoIsfMax: Boolean,
    /** SMB was allowed by the even target rule ("SMB enabled; current target ... even"). */
    val evenTargetSmb: Boolean,
    /** The SMB delivery ratio was limited by `smb_delivery_ratio_max`. */
    val smbRatioAtMax: Boolean,
    /** minGuardBG was projected below the threshold, so SMB was switched off. */
    val smbStoppedByMinGuard: Boolean,
    val maxBolus: Double?,
    /** AutoISF settings from the profile line logged just before this run, or null if none was seen. */
    val settings: Map<String, Double>?,
    /** Carbs on board in g at the run, null when the line did not have it. */
    val cob: Double? = null,
    /** A temporary target was active (from the profile line before this run). */
    val tempTarget: Boolean = false,
    /**
     * Slot the run used, from the "AutoISF slot: ..." line: a slot label such as "Breakfast",
     * "Breakfast-weekend" or "Night" (see `AutoIsfSlots.Slot.label`), "none" (slots on, outside all
     * windows) or null (line not present, older build or slots off).
     */
    val slotName: String? = null
) {

    /** The SMB was as big as `maxBolus` allowed (the last bolus step is lost by rounding down). */
    val smbAtMaxBolus: Boolean
        get() = smbUnits > 0.0 && maxBolus != null && smbUnits >= maxBolus - MAX_BOLUS_TOLERANCE

    /** acce_ISF was the strongest factor that pushed ISF up. */
    val acceDriven: Boolean
        get() = (acceIsf ?: 0.0) > 1.0 && (acceIsf ?: 0.0) >= maxOf(bgIsf ?: 0.0, ppIsf ?: 0.0, duraIsf ?: 0.0)

    /** bg_ISF was the strongest factor that pushed ISF up. */
    val bgDriven: Boolean
        get() = (bgIsf ?: 0.0) > 1.0 && (bgIsf ?: 0.0) >= maxOf(acceIsf ?: 0.0, ppIsf ?: 0.0, duraIsf ?: 0.0)

    companion object {

        private const val MAX_BOLUS_TOLERANCE = 0.06
    }
}

/**
 * Reads AutoISF loop runs out of AAPS log lines.
 *
 * Feed it every line, oldest first, with [accept]. Only three kinds of lines matter and all of them
 * are written by `OpenAPSAutoISFPlugin` for each run:
 * - `Glucose status: GlucoseStatusAutoIsf(...)` - gives the time of the BG reading
 * - `Profile: OapsProfileAutoIsf(...)` - gives the settings used by the run
 * - `Result: RT(algorithm=AUTO_ISF, ...)` - the result, with the reason and the AutoISF console text
 *
 * The parser does not fail on a broken line, it just skips it. A log is written by many threads,
 * and a line can be cut at the end of a file.
 */
class AutoIsfLogParser {

    private val _runs = mutableListOf<AutoIsfLogRun>()
    val runs: List<AutoIsfLogRun> get() = _runs

    private var lastSettings: Map<String, Double>? = null
    private var lastTempTarget = false
    private var lastBgTimestamp: Long? = null

    fun accept(line: String) {
        when {
            line.contains(RESULT_MARK)  -> parseResult(line)?.let { _runs.add(it) }
            line.contains(PROFILE_MARK) -> {
                lastSettings = parseSettings(line)
                lastTempTarget = line.contains(TEMP_TARGET_SET)
            }
            line.contains(GLUCOSE_MARK) -> lastBgTimestamp = GLUCOSE_DATE.find(line)?.groupValues?.get(1)?.toLongOrNull()
        }
    }

    private fun parseResult(line: String): AutoIsfLogRun? {
        val timestamp = TIMESTAMP.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val bg = number(BG, line) ?: return null
        return AutoIsfLogRun(
            timestamp = timestamp,
            bgTimestamp = lastBgTimestamp,
            bg = bg,
            eventualBg = number(EVENTUAL_BG, line),
            targetBg = number(TARGET_BG, line),
            insulinReq = number(INSULIN_REQ, line),
            smbUnits = number(UNITS, line) ?: 0.0,
            iob = number(IOB, line),
            finalIsfFactor = number(FINAL_FACTOR, line),
            acceIsf = number(ACCE_ISF, line),
            bgIsf = number(BG_ISF, line),
            ppIsf = number(PP_ISF, line),
            duraIsf = number(DURA_ISF, line),
            limitedByAutoIsfMax = line.contains(LIMITED_BY_MAX),
            evenTargetSmb = EVEN_TARGET.containsMatchIn(line),
            smbRatioAtMax = line.contains(RATIO_AT_MAX),
            smbStoppedByMinGuard = MIN_GUARD_STOP.containsMatchIn(line),
            maxBolus = number(MAX_BOLUS, line),
            settings = lastSettings,
            cob = number(COB, line),
            tempTarget = lastTempTarget,
            slotName = SLOT.find(line)?.groupValues?.get(1)
        )
    }

    private fun parseSettings(line: String): Map<String, Double> {
        val settings = mutableMapOf<String, Double>()
        for ((name, regex) in SETTING_REGEXES) {
            val match = regex.find(line) ?: continue
            val text = match.groupValues[1].trim()
            val value = when (text) {
                "true"  -> 1.0
                "false" -> 0.0
                else    -> text.toDoubleOrNull()
            } ?: continue
            settings[name] = value
        }
        return settings
    }

    private fun number(regex: Regex, line: String): Double? =
        regex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()

    companion object {

        const val RESULT_MARK = "Result: RT(algorithm=AUTO_ISF"
        const val PROFILE_MARK = "OapsProfileAutoIsf("
        const val GLUCOSE_MARK = "GlucoseStatusAutoIsf("

        /** The settings that change how AutoISF doses. A change in one of them starts a new period. */
        val SETTING_NAMES = listOf(
            "autoISF_max", "autoISF_min", "bgAccel_ISF_weight", "bgBrake_ISF_weight", "pp_ISF_weight",
            "lower_ISFrange_weight", "higher_ISFrange_weight", "dura_ISF_weight",
            "smb_delivery_ratio", "smb_delivery_ratio_min", "smb_delivery_ratio_max", "smb_delivery_ratio_bg_range",
            "smb_max_range_extension", "enableSMB_EvenOn_OddOff_always", "iob_threshold_percent",
            "maxSMBBasalMinutes", "maxUAMSMBBasalMinutes"
        )

        private val SETTING_REGEXES = SETTING_NAMES.map { it to Regex("(?:\\(|, )$it=([^,)]+)") }

        private const val NUM ="(-?[0-9]+(?:\\.[0-9]+)?(?:E-?[0-9]+)?)"

        private val TIMESTAMP = Regex("[(,] timestamp=([0-9]+)")
        private val GLUCOSE_DATE = Regex(", date=([0-9]+)")
        private val BG = Regex(", bg=$NUM")
        private val EVENTUAL_BG = Regex(", eventualBG=$NUM")
        private val TARGET_BG = Regex(", targetBG=$NUM")
        private val INSULIN_REQ = Regex(", insulinReq=$NUM")
        private val UNITS = Regex(", units=$NUM")
        private val IOB = Regex(", IOB=$NUM")
        private val FINAL_FACTOR = Regex("final ISF factor is $NUM")
        private val ACCE_ISF = Regex("acce_ISF adaptation is $NUM")
        private val BG_ISF = Regex("bg_ISF adaptation is $NUM")
        private val PP_ISF = Regex("pp_ISF adaptation is $NUM")
        private val DURA_ISF = Regex("dura_ISF adaptation is $NUM")
        private val MAX_BOLUS = Regex("maxBolus: $NUM")
        private val COB = Regex(", COB=$NUM, IOB=")
        private val SLOT = Regex("AutoISF slot: ([A-Za-z-]+)")
        private const val TEMP_TARGET_SET = "temptargetSet=true"
        private val EVEN_TARGET = Regex("SMB enabled; current target \\S+ (has|is) even")
        private val MIN_GUARD_STOP = Regex("minGuardBG \\S+ projected below \\S+ - disabling SMB")
        private const val LIMITED_BY_MAX = "limited by autoISF_max"
        private const val RATIO_AT_MAX = "SMB delivery ratio limited by maximum value"
    }
}
