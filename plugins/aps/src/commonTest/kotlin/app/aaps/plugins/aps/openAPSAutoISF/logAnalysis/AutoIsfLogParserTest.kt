package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoIsfLogParserTest {

    /** Real lines from an AAPS log (AutoISF 3.0.1), one run at 14:09 with an SMB and one at 14:19. */
    private val sampleLines = listOf(
        """14:09:09.070 [DefaultDispatcher-worker-3] D/APS: [OpenAPSAutoISFPlugin.invoke():438]: Glucose status:     GlucoseStatusAutoIsf(glucose=175.0, noise=0.0, delta=-2.0, shortAvgDelta=-3.39, longAvgDelta=-4.25, date=1793106539632, duraISFminutes=15.0, duraISFaverage=180.5, parabolaMinutes=20.0, deltaPl=-3.6571428571432336, deltaPn=-3.2285714285718052, bgAcceleration=0.4285714285714286, a0=174.6, a1=-3.44, a2=0.21, corrSqu=0.9724)""",
        """14:09:09.071 [DefaultDispatcher-worker-3] D/APS: [OpenAPSAutoISFPlugin.invoke():441]: Profile:            OapsProfileAutoIsf(dia=0.0, min_5m_carbimpact=0.0, max_iob=6.0, max_daily_basal=0.8, max_basal=1.9, min_bg=111.7, max_bg=111.7, target_bg=111.696658, carb_ratio=12.0, sens=40.82232607388888, autosens_adjust_targets=false, max_daily_safety_multiplier=4.0, current_basal_safety_multiplier=5.0, high_temptarget_raises_sensitivity=false, low_temptarget_lowers_sensitivity=false, sensitivity_raises_target=false, resistance_lowers_target=false, adv_target_adjustments=false, exercise_mode=false, half_basal_exercise_target=160, maxCOB=120, skip_neutral_temps=false, remainingCarbsCap=90, enableUAM=true, A52_risk_enable=false, SMBInterval=5, enableSMB_with_COB=true, enableSMB_with_temptarget=true, allowSMB_with_high_temptarget=false, enableSMB_always=true, enableSMB_after_carbs=false, maxSMBBasalMinutes=30, maxUAMSMBBasalMinutes=30, bolus_increment=0.05, carbsReqThreshold=5, current_basal=0.3875, temptargetSet=false, autosens_max=1.6, out_units=mmol/L, lgsThreshold=95, variable_sens=31.1, autoISF_version=3.0.1, enable_autoISF=true, autoISF_max=1.600000023841858, autoISF_min=0.6000000238418579, bgAccel_ISF_weight=0.30000001192092896, bgBrake_ISF_weight=0.08, pp_ISF_weight=0.09000000357627869, lower_ISFrange_weight=0.3, higher_ISFrange_weight=0.6, dura_ISF_weight=0.029999999329447746, smb_delivery_ratio=0.5, smb_delivery_ratio_min=0.6, smb_delivery_ratio_max=0.800000011920929, smb_delivery_ratio_bg_range=0.25, smb_max_range_extension=5.0, enableSMB_EvenOn_OddOff_always=true, iob_threshold_percent=90, profile_percentage=100)""",
        """14:09:09.072 [DefaultDispatcher-worker-3] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, timestamp=1793106549036, temp=absolute, bg=175.0, tick=-2, eventualBG=150.0, targetBG=111.7, snoozeBG=null, insulinReq=1.23, carbsReq=null, carbsReqWithin=null, units=0.95, deliverAt=1793106549036, sensitivityRatio=1.0, reason=COB: 0, Dev: -1.4, BGI: 0.0, ISF: 1.7, CR: 12, Target: 6.2, minPredBG 9.2, minGuardBG 8.7, IOBpredBG 8.7; Eventual BG 8.3 >= 6.2,  insulinReq 1.23. Microbolusing 0.95U. adj. req. rate: 2.85 to maxSafeBasal: 1.9, temp 0.35 < 1.9U/hr. , duration=30, rate=1.9, predBGs=Predictions(IOB=[175, 172, 169, 166, 164, 162, 161, 159, 158, 157, 157, 156, 156], ZT=[175, 175, 175, 175, 175, 175, 175, 175, 176, 176, 176, 176, 177, 177, 178, 178, 178, 179, 180, 180, 181, 181, 182, 183, 184, 184, 185, 186, 187, 187, 188, 189, 190, 191, 192, 193, 193], COB=null, aCOB=null, UAM=null), COB=0.0, IOB=-0.003, variable_sens=31.1, isfMgdlForCarbs=null, consoleLog=[], consoleError=[Autosens ratio: 1.0; , Basal unchanged: 0.3875;, ISF unchanged: 40.8, CR: 12.0, ----------------------------------, start AutoISF 3.0.1, ----------------------------------, User setting iobTH=90% not modulated, SMB enabled; current target 6.2 has even decimal, Loop allows medium power, SMB delivery ratio limited by maximum value 0.8, outside meal time: meal factors scaled by 1.0, Parabola fit results were acceleration:0.43, correlation:0.9724, duration:20.0m, Parabolic fit extrapolates a minimum of 8.9 in about 41.0 minutes, acce_ISF adaptation is 1.09, bg_ISF adaptation is 1.31, pp_ISF adaptation by-passed as no rise or too short lived, dura_ISF adaptation is 1.0 because ISF 40.8 did not do it for 15.0m, final ISF factor is 1.31 , ----------------------------------, end AutoISF, ----------------------------------, profile.sens: 40.82232607388888, sens: 31.1, CSF: 2.591666666666667, Carb Impact: -3.4 mg/dL per 5m; CI Duration: 0.0 hours; remaining CI (~2h peak): 0.0 mg/dL per 5m, UAM Impact: -3.4 mg/dL per 5m; UAM Duration: 0.0 hours, EventualBG is 150.0 ;, minPredBG: 166.0 minIOBPredBG: 156.0 minZTGuardBG: 175.0,  minUAMPredBG: 156.0,  avgPredBG: 175.0 COB: 0.0 / 0.0, BG projected to remain above 6.2 for 240 minutes, naive_eventualBG: 175.0 bgUndershoot: -99.15 zeroTempDuration 240 zeroTempEffect: 48 carbsReq: -57, profile.maxSMBBasalMinutes: 30 profile.current_basal: 0.3875, naive_eventualBG 175.0,0m 0.0U/h temp needed; last bolus 2.98851092E7m ago; maxBolus: 1.0])""",
        """14:19:08.308 [DefaultDispatcher-worker-21] D/APS: [OpenAPSAutoISFPlugin.invoke():438]: Glucose status:     GlucoseStatusAutoIsf(glucose=163.0, noise=0.0, delta=-11.0, shortAvgDelta=-7.22, longAvgDelta=-4.89, date=1793107139632, duraISFminutes=0.0, duraISFaverage=163.0, parabolaMinutes=15.0, deltaPl=-8.799999999999919, deltaPn=-13.299999999999807, bgAcceleration=-4.499999999999886, a0=163.6, a1=-11.05, a2=-2.25, corrSqu=0.9491)""",
        """14:19:08.310 [DefaultDispatcher-worker-21] D/APS: [OpenAPSAutoISFPlugin.invoke():441]: Profile:            OapsProfileAutoIsf(dia=0.0, min_5m_carbimpact=0.0, max_iob=6.0, max_daily_basal=0.8, max_basal=1.9, min_bg=111.7, max_bg=111.7, target_bg=111.696658, carb_ratio=12.0, sens=40.82232607388888, autosens_adjust_targets=false, max_daily_safety_multiplier=4.0, current_basal_safety_multiplier=5.0, high_temptarget_raises_sensitivity=false, low_temptarget_lowers_sensitivity=false, sensitivity_raises_target=false, resistance_lowers_target=false, adv_target_adjustments=false, exercise_mode=false, half_basal_exercise_target=160, maxCOB=120, skip_neutral_temps=false, remainingCarbsCap=90, enableUAM=true, A52_risk_enable=false, SMBInterval=5, enableSMB_with_COB=true, enableSMB_with_temptarget=true, allowSMB_with_high_temptarget=false, enableSMB_always=true, enableSMB_after_carbs=false, maxSMBBasalMinutes=30, maxUAMSMBBasalMinutes=30, bolus_increment=0.05, carbsReqThreshold=5, current_basal=0.3875, temptargetSet=false, autosens_max=1.6, out_units=mmol/L, lgsThreshold=95, variable_sens=40.2, autoISF_version=3.0.1, enable_autoISF=true, autoISF_max=1.600000023841858, autoISF_min=0.6000000238418579, bgAccel_ISF_weight=0.30000001192092896, bgBrake_ISF_weight=0.08, pp_ISF_weight=0.09000000357627869, lower_ISFrange_weight=0.3, higher_ISFrange_weight=0.6, dura_ISF_weight=0.029999999329447746, smb_delivery_ratio=0.5, smb_delivery_ratio_min=0.6, smb_delivery_ratio_max=0.800000011920929, smb_delivery_ratio_bg_range=0.25, smb_max_range_extension=5.0, enableSMB_EvenOn_OddOff_always=true, iob_threshold_percent=90, profile_percentage=100)""",
        """14:19:08.312 [DefaultDispatcher-worker-21] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, timestamp=1793107148278, temp=absolute, bg=163.0, tick=-11, eventualBG=92.0, targetBG=111.7, snoozeBG=null, insulinReq=0.0, carbsReq=null, carbsReqWithin=null, units=null, deliverAt=1793107148278, sensitivityRatio=1.0, reason=COB: 0, Dev: -1.3, BGI: 0.0, ISF: 2.2, CR: 12, Target: 6.2, minPredBG 5.5, minGuardBG 3.3, IOBpredBG 3.3; minGuardBG 3.3 < 4.2, duration=120, rate=0.0, predBGs=Predictions(IOB=[163, 153, 143, 134, 126, 118, 111, 105, 99, 95, 91, 88, 86, 84, 83, 81, 79, 78, 77, 75, 74, 73, 72, 71, 70, 69, 68, 67, 67, 66, 65, 65, 64, 64, 63, 63, 63, 62, 62, 62, 61, 61, 61, 61, 61, 61, 60], ZT=[163, 162, 161, 160, 158, 156, 154, 153, 151, 149, 148, 146, 145, 143, 142, 141, 140, 139, 139, 138, 138, 137, 137, 137, 137, 137, 137, 137, 138, 138, 138, 139, 140, 140, 141, 142, 143, 143], COB=null, aCOB=null, UAM=null), COB=0.0, IOB=1.18, variable_sens=40.2, isfMgdlForCarbs=null, consoleLog=[], consoleError=[Autosens ratio: 1.0; , Basal unchanged: 0.3875;, ISF unchanged: 40.8, CR: 12.0, ----------------------------------, start AutoISF 3.0.1, ----------------------------------, User setting iobTH=90% not modulated, SMB enabled; current target 6.2 has even decimal, Loop allows medium power, SMB delivery ratio limited by maximum value 0.8, outside meal time: meal factors scaled by 1.0, Parabola fit results were acceleration:-4.5, correlation:0.9491, duration:15.0m, acce_ISF adaptation is 0.82, bg_ISF adaptation is 1.23, pp_ISF adaptation by-passed as no rise or too short lived, dura_ISF by-passed; bg is only 0.0 m at level 163.0, strongest autoISF factor 1.23 weakened to 1.02 as bg decelerates already, final ISF factor is 1.02 , ----------------------------------, end AutoISF, ----------------------------------, profile.sens: 40.82232607388888, sens: 40.2, CSF: 3.35, Carb Impact: -10.2 mg/dL per 5m; CI Duration: 0.0 hours; remaining CI (~2h peak): 0.0 mg/dL per 5m, UAM Impact: -10.2 mg/dL per 5m; UAM Duration: 0.0 hours, EventualBG is 92.0 ;, minPredBG: 99.0 minIOBPredBG: 60.0 minZTGuardBG: 137.0,  minUAMPredBG: 60.0,  avgPredBG: 137.0 COB: 0.0 / 0.0, minGuardBG 3.3 projected below 4.2 - disabling SMB, BG projected to remain above 6.2 for 30 minutes, BG projected to remain above 4.2 for 95 minutes, naive_eventualBG: 116.0 bgUndershoot: -40.150000000000006 zeroTempDuration 95 zeroTempEffect: 25 carbsReq: -19])""",
        "14:09:09.084 [w] D/APS: [LoopPlugin.applyTBRRequest():875]: applyAPSRequest: RT(algorithm=AUTO_ISF, timestamp=1, bg=1.0)"
    )

    @Test
    fun readsTheRunWithAnSmb() {
        val parser = AutoIsfLogParser()
        sampleLines.forEach { parser.accept(it) }

        assertEquals(2, parser.runs.size)
        val run = parser.runs[0]
        assertEquals(1793106549036L, run.timestamp)
        assertEquals(1793106539632L, run.bgTimestamp)
        assertEquals(175.0, run.bg)
        assertEquals(150.0, run.eventualBg)
        assertEquals(111.7, run.targetBg)
        assertEquals(1.23, run.insulinReq)
        assertEquals(0.95, run.smbUnits)
        assertEquals(-0.003, run.iob)
        assertEquals(1.31, run.finalIsfFactor)
        assertEquals(1.09, run.acceIsf)
        assertEquals(1.31, run.bgIsf)
        assertNull(run.ppIsf)
        assertEquals(1.0, run.duraIsf)
        assertEquals(1.0, run.maxBolus)
        assertFalse(run.limitedByAutoIsfMax)
        assertTrue(run.evenTargetSmb)
        assertTrue(run.smbRatioAtMax)
        assertFalse(run.smbStoppedByMinGuard)
        assertTrue(run.smbAtMaxBolus)
        assertTrue(run.bgDriven)
        assertFalse(run.acceDriven)
    }

    @Test
    fun readsCobTempTargetAndSlot() {
        val parser = AutoIsfLogParser()
        sampleLines.forEach { parser.accept(it) }
        val run = parser.runs[0]
        assertEquals(0.0, run.cob)
        assertFalse(run.tempTarget)
        assertNull(run.slotName)

        // a run of a newer build: temp target set, carbs on board, inside the dinner slot
        parser.accept("18:05:00.000 [w] D/APS: [OpenAPSAutoISFPlugin.invoke():441]: Profile:            OapsProfileAutoIsf(dia=0.0, temptargetSet=true, autoISF_max=1.5, profile_percentage=100)")
        parser.accept(
            "18:05:00.100 [w] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, timestamp=1793120700000, temp=absolute, bg=150.0, " +
                "reason=COB: 40, predBGs=Predictions(IOB=[150], COB=null), COB=40.0, IOB=1.2, consoleError=[AutoISF slot: Dinner (17:00-20:00), final ISF factor is 1.2 ])"
        )
        parser.accept(
            "21:05:00.100 [w] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, timestamp=1793131500000, temp=absolute, bg=120.0, " +
                "reason=COB: 0, predBGs=Predictions(IOB=[120], COB=null), COB=0.0, IOB=0.2, consoleError=[AutoISF slot: none, global values, final ISF factor is 1.0 ])"
        )
        // slots v2: the label has a hyphen and the line names the kind of day
        parser.accept(
            "08:05:00.100 [w] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, timestamp=1793174700000, temp=absolute, bg=140.0, " +
                "reason=COB: 30, predBGs=Predictions(IOB=[140], COB=null), COB=30.0, IOB=1.0, consoleError=[AutoISF slot: Breakfast-weekend (07:00-10:00, holiday), final ISF factor is 1.1 ])"
        )
        parser.accept(
            "02:05:00.100 [w] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, timestamp=1793239500000, temp=absolute, bg=110.0, " +
                "reason=COB: 0, predBGs=Predictions(IOB=[110], COB=null), COB=0.0, IOB=0.1, consoleError=[AutoISF slot: Night (23:00-05:30, weekday), final ISF factor is 1.0 ])"
        )
        assertEquals(6, parser.runs.size)
        val dinner = parser.runs[2]
        assertEquals(40.0, dinner.cob)
        assertTrue(dinner.tempTarget)
        assertEquals("Dinner", dinner.slotName)
        assertEquals(1.5, assertNotNull(dinner.settings).getValue("autoISF_max"), 1e-6)
        assertEquals("none", parser.runs[3].slotName)
        assertEquals("Breakfast-weekend", parser.runs[4].slotName)
        assertEquals("Night", parser.runs[5].slotName)
    }

    @Test
    fun readsTheSettingsOfTheRun() {
        val parser = AutoIsfLogParser()
        sampleLines.forEach { parser.accept(it) }

        val settings = assertNotNull(parser.runs[0].settings)
        assertEquals(1.6, settings.getValue("autoISF_max"), 1e-6)
        assertEquals(0.3, settings.getValue("bgAccel_ISF_weight"), 1e-6)
        assertEquals(0.6, settings.getValue("higher_ISFrange_weight"), 1e-6)
        assertEquals(0.8, settings.getValue("smb_delivery_ratio_max"), 1e-6)
        assertEquals(5.0, settings.getValue("smb_max_range_extension"), 1e-6)
        assertEquals(1.0, settings.getValue("enableSMB_EvenOn_OddOff_always"), 1e-6)
        assertEquals(90.0, settings.getValue("iob_threshold_percent"), 1e-6)
        // "smb_delivery_ratio" must not pick up "smb_delivery_ratio_min" or "_max"
        assertEquals(0.5, settings.getValue("smb_delivery_ratio"), 1e-6)
    }

    @Test
    fun readsTheRunWithAPredictedLow() {
        val parser = AutoIsfLogParser()
        sampleLines.forEach { parser.accept(it) }

        val run = parser.runs[1]
        assertEquals(0.0, run.smbUnits)
        assertTrue(run.smbStoppedByMinGuard)
        assertNull(run.maxBolus)
    }

    @Test
    fun skipsBrokenAndForeignLines() {
        val parser = AutoIsfLogParser()
        parser.accept("")
        parser.accept("14:09:09.072 [w] D/APS: [OpenAPSAutoISFPlugin.invoke():479]: Result: RT(algorithm=AUTO_ISF, runningDynamicIsf=true, tim")
        parser.accept("14:09:09.072 [w] D/APS: [OpenAPSSMBPlugin.invoke():400]: Result: RT(algorithm=SMB, timestamp=1, bg=100.0)")
        assertTrue(parser.runs.isEmpty())
    }
}
