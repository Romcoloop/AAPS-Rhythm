package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * iOS does not write the AAPS log files that the AutoISF log analysis reads, so the analysis is not
 * offered here: [available] is false and the card is hidden.
 */
@ContributesBinding(AppScope::class)
@Inject
class IosAutoIsfLogSource : AutoIsfLogSource {

    override val available: Boolean = false

    override suspend fun readLines(sinceMillis: Long, onLine: (String) -> Unit): Int =
        error("AutoISF log analysis is not available on iOS")
}
