package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.interfaces.logging.LoggerUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Reads the AAPS log files from [LoggerUtils.logDirectory].
 *
 * logback writes `AndroidAPS.log` and moves it to `AndroidAPS._<date>_<time>_.<n>.zip` when it is
 * 5 MB big or a new day starts (see `logback.xml`). A zip holds the lines written before the time
 * it was last modified, so a zip changed before [readLines]' `sinceMillis` can be skipped.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidAutoIsfLogSource(
    private val loggerUtils: LoggerUtils
) : AutoIsfLogSource {

    override val available: Boolean = true

    override suspend fun readLines(sinceMillis: Long, onLine: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        val directory = File(loggerUtils.logDirectory)
        val files = directory.listFiles { file ->
            file.isFile && file.lastModified() >= sinceMillis &&
                (file.name == CURRENT_LOG || (file.name.startsWith(ARCHIVE_PREFIX) && file.name.endsWith(ARCHIVE_SUFFIX)))
        }.orEmpty()
            // The current log is the newest. Zips sort by time of change.
            .sortedWith(compareBy<File> { it.name == CURRENT_LOG }.thenBy { it.lastModified() })

        var count = 0
        for (file in files) {
            ensureActive()
            if (file.name == CURRENT_LOG) file.bufferedReader().useLines { lines -> lines.forEach(onLine) }
            else readZip(file, onLine)
            count++
        }
        count
    }

    private fun readZip(file: File, onLine: (String) -> Unit) {
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Do not close this reader: closing it would close the zip stream and lose the next entries.
                val reader = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
                entry = zip.nextEntry
            }
        }
    }

    private companion object {

        const val CURRENT_LOG = "AndroidAPS.log"
        const val ARCHIVE_PREFIX = "AndroidAPS._"
        const val ARCHIVE_SUFFIX = ".zip"
    }
}
