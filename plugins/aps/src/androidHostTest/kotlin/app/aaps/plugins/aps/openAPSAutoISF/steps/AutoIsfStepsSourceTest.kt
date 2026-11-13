package app.aaps.plugins.aps.openAPSAutoISF.steps

import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The switch "Use the phone as step source" picks ONE source. These tests keep the two sources apart
 * and check the watch values. A watch app before AAPS #5499 sends its 10 and 15 minute values as
 * single 5 minute blocks and they must be added up before the rules read them. A newer watch app
 * sends sums and they must not be added up again.
 */
class AutoIsfStepsSourceTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var phone: PhoneStepCounter

    private lateinit var steps: AutoIsfSteps
    private val now = 1_700_000_000_000L
    private val phoneName = "Test phone"
    private val watchName = "Test watch"

    @BeforeEach fun prepare() {
        steps = AutoIsfSteps(preferences, persistenceLayer, phone, aapsLogger) { 0 }
        whenever(phone.available).thenReturn(true)
        whenever(phone.deviceName).thenReturn(phoneName)
    }

    private fun switchOn(on: Boolean) {
        whenever(preferences.get(BooleanKey.ApsAutoIsfStepsPhoneSource)).thenReturn(on)
    }

    private fun row(device: String, timestamp: Long = now - 60_000L, block5: Int = 548, block10: Int = 193, block15: Int = 100) = SC(
        duration = 180 * 60 * 1000L, timestamp = timestamp,
        steps5min = block5, steps10min = block10, steps15min = block15,
        steps30min = 1672, steps60min = 1890, steps180min = 2245, device = device,
    )

    private val lengths = listOf(5, 10, 15, 30, 60, 180)

    /** One sample of a watch app before AAPS #5499: six rows, each filled up to its own length only. */
    private fun blockSample(block5: Int, block10: Int, block15: Int, steps30: Int, steps60: Int, steps180: Int): List<SC> =
        lengths.map { minutes ->
            SC(
                duration = minutes * 60 * 1000L, timestamp = now - 60_000L,
                steps5min = block5,
                steps10min = if (minutes >= 10) block10 else 0,
                steps15min = if (minutes >= 15) block15 else 0,
                steps30min = if (minutes >= 30) steps30 else 0,
                steps60min = if (minutes >= 60) steps60 else 0,
                steps180min = if (minutes >= 180) steps180 else 0,
                device = watchName,
            )
        }

    /** One sample of a watch app from AAPS #5499 on: six rows, every row has all six sums. */
    private fun sumSample(steps5: Int, steps10: Int, steps15: Int, steps30: Int, steps60: Int, steps180: Int): List<SC> =
        lengths.map { minutes ->
            SC(
                duration = minutes * 60 * 1000L, timestamp = now - 60_000L,
                steps5min = steps5, steps10min = steps10, steps15min = steps15,
                steps30min = steps30, steps60min = steps60, steps180min = steps180,
                device = watchName,
            )
        }

    private fun phoneWindows() = StepWindows(
        steps5 = 10, steps10 = 20, steps15 = 30, steps30 = 40, steps60 = 50, steps180 = 60, timestamp = now,
    )

    @Test
    fun `watch rows are turned into sums`() = runTest {
        switchOn(false)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(row(watchName)))

        val w = steps.windows(now)!!

        assertThat(w.source).isEqualTo(StepSource.WATCH)
        assertThat(w.steps5).isEqualTo(548)
        assertThat(w.steps10).isEqualTo(741)   // 548 + 193
        assertThat(w.steps15).isEqualTo(841)   // 548 + 193 + 100
        assertThat(w.steps30).isEqualTo(1672)
        assertThat(w.steps60).isEqualTo(1890)
        assertThat(w.steps180).isEqualTo(2245)
    }

    @Test
    fun `the blocks of an older watch app are added up`() = runTest {
        switchOn(false)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any()))
            .thenReturn(blockSample(block5 = 548, block10 = 193, block15 = 100, steps30 = 1672, steps60 = 1890, steps180 = 2245))

        val w = steps.windows(now)!!

        assertThat(w.source).isEqualTo(StepSource.WATCH)
        assertThat(w.steps5).isEqualTo(548)
        assertThat(w.steps10).isEqualTo(741)   // 548 + 193
        assertThat(w.steps15).isEqualTo(841)   // 548 + 193 + 100
        assertThat(w.steps30).isEqualTo(1672)
        assertThat(w.steps60).isEqualTo(1890)
        assertThat(w.steps180).isEqualTo(2245)
    }

    @Test
    fun `the sums of a newer watch app are not added up again`() = runTest {
        switchOn(false)
        // The same walk as in the test above. Added up again it would be 1289 and 2130.
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any()))
            .thenReturn(sumSample(steps5 = 548, steps10 = 741, steps15 = 841, steps30 = 1672, steps60 = 1890, steps180 = 2245))

        val w = steps.windows(now)!!

        assertThat(w.source).isEqualTo(StepSource.WATCH)
        assertThat(w.steps5).isEqualTo(548)
        assertThat(w.steps10).isEqualTo(741)
        assertThat(w.steps15).isEqualTo(841)
        assertThat(w.steps30).isEqualTo(1672)
        assertThat(w.steps60).isEqualTo(1890)
        assertThat(w.steps180).isEqualTo(2245)
    }

    @Test
    fun `the newest watch row wins and rows from the phone are ignored`() = runTest {
        switchOn(false)
        val oldWatch = row(watchName, timestamp = now - 300_000L, block5 = 1)
        val newerPhone = row(phoneName, timestamp = now - 10_000L, block5 = 999)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(oldWatch, newerPhone))

        val w = steps.windows(now)!!

        assertThat(w.steps5).isEqualTo(1)
    }

    @Test
    fun `with the switch off only phone rows means no data`() = runTest {
        switchOn(false)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(row(phoneName)))

        assertThat(steps.windows(now)).isNull()
    }

    @Test
    fun `with the switch off the phone sensor is not read`() = runTest {
        switchOn(false)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(row(watchName)))

        steps.windows(now)

        verify(phone, never()).windows(any())
    }

    @Test
    fun `with the switch on the phone is the source and the database is not read`() = runTest {
        switchOn(true)
        whenever(phone.windows(now)).thenReturn(phoneWindows())

        val w = steps.windows(now)!!

        assertThat(w.source).isEqualTo(StepSource.PHONE)
        assertThat(w.steps10).isEqualTo(20)   // a sum already, not changed
        verify(persistenceLayer, never()).getStepsCountFromTimeToTime(any(), any())
    }

    @Test
    fun `with the switch on and no phone data there is no fallback to the watch`() = runTest {
        switchOn(true)
        whenever(phone.windows(now)).thenReturn(null)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(row(watchName)))

        assertThat(steps.windows(now)).isNull()
    }

    @Test
    fun `with the switch on and no step sensor there is no data`() = runTest {
        switchOn(true)
        whenever(phone.available).thenReturn(false)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(row(watchName)))

        assertThat(steps.windows(now)).isNull()
    }

    @Test
    fun `only rows of the last 15 minutes are asked for`() = runTest {
        switchOn(false)
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(emptyList())

        steps.windows(now)

        verify(persistenceLayer).getStepsCountFromTimeToTime(now - AutoIsfSteps.MAX_AGE_MS, now)
    }

    private fun guardOn(guard30: Int) {
        whenever(preferences.get(BooleanKey.ApsAutoIsfStepsSportGuard)).thenReturn(true)
        whenever(preferences.get(IntKey.ApsAutoIsfStepsMildSteps60)).thenReturn(2500)
        whenever(preferences.get(IntKey.ApsAutoIsfStepsGuardSteps30)).thenReturn(guard30)
        whenever(preferences.get(IntKey.ApsAutoIsfStepsGuardSteps60)).thenReturn(4000)
        whenever(preferences.get(IntKey.ApsAutoIsfStepsLongSteps60)).thenReturn(6000)
        whenever(preferences.get(IntKey.ApsAutoIsfStepsLongSteps180)).thenReturn(7000)
    }

    @Test
    fun `the sport guard starts at the step limit from the settings`() = runTest {
        switchOn(false)
        // 30 minutes = 1672 steps, walking at about 110 steps a minute
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any())).thenReturn(listOf(row(watchName)))

        guardOn(guard30 = 2000)
        val before = steps.decide(now, 100.0, 0.0, 0L)
        assertThat(before.basalFactor).isEqualTo(1.0)
        assertThat(before.sportGuard).isFalse()

        guardOn(guard30 = 1500)
        val after = steps.decide(now, 100.0, 0.0, 0L)
        assertThat(after.basalFactor).isEqualTo(0.0)
        assertThat(after.sportGuard).isTrue()
        assertThat(after.log).contains("steps (watch) 5m=548 10m=741")
    }

    @Test
    fun `one short burst from a newer watch app does not start the sport guard`() = runTest {
        switchOn(false)
        guardOn(guard30 = 2000)
        // 450 steps in the last finished 5 minutes and none before. The fast start needs 400 in
        // 5 minutes and 800 in 10 minutes. Read as blocks the 10 minutes would be 450 + 450 = 900
        // and the guard would stop the basal for a walk that did not happen.
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any()))
            .thenReturn(sumSample(steps5 = 450, steps10 = 450, steps15 = 450, steps30 = 450, steps60 = 450, steps180 = 450))

        val newer = steps.decide(now, 100.0, 0.0, 0L)

        assertThat(newer.sportGuard).isFalse()
        assertThat(newer.basalFactor).isEqualTo(1.0)
        assertThat(newer.log).contains("steps (watch) 5m=450 10m=450")

        // The same burst from an older watch app gives the same answer.
        whenever(persistenceLayer.getStepsCountFromTimeToTime(any(), any()))
            .thenReturn(blockSample(block5 = 450, block10 = 0, block15 = 0, steps30 = 450, steps60 = 450, steps180 = 450))

        val older = steps.decide(now, 100.0, 0.0, 0L)

        assertThat(older.sportGuard).isFalse()
        assertThat(older.basalFactor).isEqualTo(1.0)
        assertThat(older.log).contains("steps (watch) 5m=450 10m=450")
    }
}
