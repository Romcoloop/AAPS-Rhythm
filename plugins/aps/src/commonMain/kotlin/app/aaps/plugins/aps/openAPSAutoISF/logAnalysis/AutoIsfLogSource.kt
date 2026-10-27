package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

/**
 * Gives the lines of the AAPS log files to the AutoISF log analysis.
 *
 * Reading files is the only platform part of the analysis, so it sits behind this interface.
 * Android reads `AndroidAPS.log` and the zipped older logs next to it.
 */
interface AutoIsfLogSource {

    /**
     * False on a platform that has no AAPS log files to read. The log analysis card is then not
     * shown at all, rather than shown and always empty.
     */
    val available: Boolean

    /**
     * Calls [onLine] for every line of the log files that may hold entries written at or after
     * [sinceMillis], oldest file first. A file can also hold older lines; the caller filters by the
     * time inside the line.
     *
     * Runs on the calling coroutine and can be cancelled between files.
     *
     * @return number of files read
     */
    suspend fun readLines(sinceMillis: Long, onLine: (String) -> Unit): Int
}
