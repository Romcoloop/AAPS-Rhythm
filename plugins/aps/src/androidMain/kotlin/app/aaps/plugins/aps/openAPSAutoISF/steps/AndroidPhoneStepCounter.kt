package app.aaps.plugins.aps.openAPSAutoISF.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.plugins.aps.ApsStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * The phone's `TYPE_STEP_COUNTER` sensor behind [PhoneStepCounter].
 *
 * The sensor reports all steps since the phone was started, as one growing number. Each event is
 * handed to [StepBuckets], which books the difference to the current minute. It must stay a
 * *counter* sensor: a `TYPE_STEP_DETECTOR` reports one step per event, and subtracting the last value
 * would be wrong for it.
 *
 * Android needs the `ACTIVITY_RECOGNITION` permission for this sensor. Without it the sensor stays
 * quiet and [windows] answers null, so no rule fires.
 */
@ContributesBinding(AppScope::class, binding = binding<PhoneStepCounter>())
@SingleIn(AppScope::class)
@Inject
class AndroidPhoneStepCounter(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
) : PhoneStepCounter, SensorEventListener {

    private val sensorManager: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val buckets = StepBuckets()
    private var listening = false

    override val available: Boolean get() = sensor != null

    override fun permissions(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(Manifest.permission.ACTIVITY_RECOGNITION),
            rationaleTitle = ApsStrings.permission_steps_title,
            rationaleDescription = ApsStrings.permission_steps_description,
        )
    )

    override val permissionGranted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    override val deviceName: String = "${Build.MANUFACTURER ?: "unknown"} ${Build.MODEL ?: "unknown"} phone"

    @Synchronized
    override fun start() {
        if (listening) return
        val manager = sensorManager
        val stepSensor = sensor
        if (manager == null || stepSensor == null) {
            aapsLogger.debug(LTag.APS, "Phone step counter: no step sensor on this phone")
            return
        }
        if (!permissionGranted) {
            aapsLogger.debug(LTag.APS, "Phone step counter: ACTIVITY_RECOGNITION not granted, not listening")
            return
        }
        // Report latency 0: we want each batch as soon as the sensor has it, not minutes later.
        listening = manager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
        aapsLogger.debug(LTag.APS, "Phone step counter: listening=$listening")
    }

    @Synchronized
    override fun stop() {
        if (listening) {
            sensorManager?.unregisterListener(this)
            listening = false
            aapsLogger.debug(LTag.APS, "Phone step counter: stopped")
        }
        buckets.clear()
    }

    @Synchronized
    override fun windows(nowMs: Long): StepWindows? = buckets.windows(nowMs)

    @Synchronized
    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.isEmpty()) return
        buckets.add(values[0].toLong(), System.currentTimeMillis())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        aapsLogger.debug(LTag.APS, "Phone step counter: accuracy $accuracy")
    }
}
