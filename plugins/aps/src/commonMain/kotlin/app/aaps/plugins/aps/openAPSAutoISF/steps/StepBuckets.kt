package app.aaps.plugins.aps.openAPSAutoISF.steps

/**
 * Steps per minute, kept for the last three hours, built from a running step counter.
 *
 * A phone step counter sensor reports one growing number: all steps since the phone was started.
 * [add] takes that number and books the difference to the current minute. [windows] then sums the
 * minutes. The maths is in common code so it can be tested without a sensor; the Android side only
 * forwards sensor events.
 *
 * `null` from [windows] means "no sensor event yet". Until the first event we cannot tell a missing
 * sensor from a person who did not walk yet, so we do not claim zero steps. After the first event an
 * empty minute is a real zero. The one limit: a sensor that stops later still reads as zero.
 */
class StepBuckets {

    private val stepsByMinute = LinkedHashMap<Long, Int>()
    private var lastCounter: Long = -1L
    private var lastEventMs: Long = 0L

    /**
     * Books a new reading of the running counter at [nowMs].
     *
     * A counter below the last one means the counter started again (for example after a reboot).
     * That reading only sets the new base line; no steps are booked for it.
     */
    fun add(counter: Long, nowMs: Long) {
        val minute = nowMs / MINUTE_MS
        if (lastCounter >= 0L && counter >= lastCounter) {
            val delta = (counter - lastCounter).toInt()
            stepsByMinute[minute] = (stepsByMinute[minute] ?: 0) + delta
        }
        lastCounter = counter
        lastEventMs = nowMs
        val keepFrom = minute - KEEP_MINUTES
        stepsByMinute.entries.removeAll { it.key < keepFrom }
    }

    /** Sums of the minutes that end at [nowMs], or null before the first sensor event. */
    fun windows(nowMs: Long): StepWindows? {
        if (lastCounter < 0L) return null
        val minute = nowMs / MINUTE_MS
        fun sum(minutes: Int): Int {
            val from = minute - minutes + 1
            var total = 0
            for ((key, value) in stepsByMinute) if (key >= from && key <= minute) total += value
            return total
        }
        return StepWindows(
            steps5 = sum(5),
            steps10 = sum(10),
            steps15 = sum(15),
            steps30 = sum(30),
            steps60 = sum(60),
            steps180 = sum(180),
            timestamp = nowMs,
        )
    }

    /** Milliseconds of the last sensor event, 0 before the first one. */
    val lastEventMillis: Long get() = lastEventMs

    /** Forgets everything, for example when the sensor is stopped. */
    fun clear() {
        stepsByMinute.clear()
        lastCounter = -1L
        lastEventMs = 0L
    }

    companion object {

        const val MINUTE_MS = 60_000L

        /** Three hours, the longest window, plus a little slack. */
        private const val KEEP_MINUTES = 185L
    }
}
