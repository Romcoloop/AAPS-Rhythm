package app.aaps.plugins.aps.openAPSAutoISF.steps

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * The step counter sensor of the phone itself, so AutoISF can read steps without a watch.
 *
 * The platform code behind this only listens to the sensor and feeds [StepBuckets]. Everything
 * else (writing the steps to the database, the dosing rules) is common code in
 * `OpenAPSAutoISFPlugin` and [StepsActivityRules].
 *
 * A platform without a step sensor answers [available] = false and never gives data. The feature
 * is then visibly absent, not present and dead: the plugin says so in its log.
 */
interface PhoneStepCounter {

    /** True when this platform has a step counter sensor to listen to. */
    val available: Boolean

    /** The runtime permissions the sensor needs; empty where the platform asks for none. */
    fun permissions(): List<PermissionGroup>

    /** True when every permission in [permissions] is granted. */
    val permissionGranted: Boolean

    /** Starts listening. Safe to call again while already listening. */
    fun start()

    /** Stops listening and forgets the counted steps. */
    fun stop()

    /** The steps counted up to now, or null when there is no data yet. */
    fun windows(nowMs: Long): StepWindows?

    /** Name of the phone, stored with the steps as the source device. */
    val deviceName: String
}
