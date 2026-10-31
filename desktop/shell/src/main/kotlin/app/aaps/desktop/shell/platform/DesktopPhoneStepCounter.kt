package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.plugins.aps.openAPSAutoISF.steps.PhoneStepCounter
import app.aaps.plugins.aps.openAPSAutoISF.steps.StepWindows
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * A desktop has no step sensor, so the feature is visibly absent here: [available] is false, there
 * is no data and no step rule ever fires.
 */
@ContributesBinding(AppScope::class)
@Inject
class DesktopPhoneStepCounter : PhoneStepCounter {

    override val available: Boolean = false
    override fun permissions(): List<PermissionGroup> = emptyList()
    override val permissionGranted: Boolean = false
    override fun start() = Unit
    override fun stop() = Unit
    override fun windows(nowMs: Long): StepWindows? = null
    override val deviceName: String = "desktop"
}
