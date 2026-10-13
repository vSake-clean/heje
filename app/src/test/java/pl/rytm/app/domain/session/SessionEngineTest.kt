package pl.rytm.app.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.data.session.SessionJson
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.planOf
import pl.rytm.app.routineOf
import kotlin.random.Random

class SessionEngineTest {

    private val t0 = 5_000_000L
    private fun clock(elapsed: Long, boot: Int = 1) = ClockReading(t0 + elapsed, 1_700_000_000_000L + elapsed, boot)
    private fun start(mode: ScheduleMode = ScheduleMode.STRICT, behavior: InterruptionBehavior = InterruptionBehavior.CONTINUE_SCHEDULE, timeoutSec: Int = 30) =
        SessionEngine.start("s", planOf(routineOf(mode = mode, interruption = behavior, flexTimeoutSec = timeoutSec)), clock(0))

    @Test
    fun `strict mode switches tasks exactly at scheduled boundaries`() {
        val s = start()
        fun at(sec: Double) = SessionEngine.evaluate(s, t0 + (sec * 1000).toLong())
        assertEquals(Phase.INTRO, at(0.0).phase)
        assertEquals(Phase.INTRO, at(9.999).phase)
        assertEquals(Phase.ACTIVE, at(10.0).phase)
        assertEquals(0, at(10.0).taskIndex)
        assertEquals(0, at(24.999).taskIndex)
        assertEquals(1, at(25.0).taskIndex) // dokładnie 15 s później – bez czekania na potwierdzenie
        assertEquals(2, at(40.0).taskIndex)
        assertEquals(3, at(100.0).taskIndex)
        assertEquals(4, at(130.0).taskIndex)
        assertEquals(Phase.FINISHED, at(310.0).phase)
    }

    @Test
    fun `state does not depend on how often it is evaluated (no drift)`() {
        val s = start()
        val rnd = Random(42)
        var t = 0L
        while (t < 320_000) {
            val st = SessionEngine.evaluate(s, t0 + t)
            val expected = when {
                t < 10_000 -> null
                t < 25_000 -> 0
                t < 40_000 -> 1
                t < 100_000 -> 2
                t < 130_000 -> 3
                t < 310_000 -> 4
                else -> null
            }
            assertEquals("t=$t", expected, if (st.phase == Phase.ACTIVE) st.taskIndex else null)
            t += rnd.nextLong(1, 7_000) // nieregularne „tyknięcia”, także bardzo rzadkie (wygaszony ekran)
        }
    }

    @Test
    fun `strict mode ignores confirmations and never shifts the schedule`() {
        var s = start()
        s = SessionEngine.confirm(s, clock(12_000))
        assertEquals(0, SessionEngine.evaluate(s, t0 + 20_000).taskIndex)
        assertEquals(1, SessionEngine.evaluate(s, t0 + 25_000).taskIndex)
        assertTrue(s.completedShifts.isEmpty())
    }

    @Test
    fun `pause freezes position and resume continues from the same point`() {
        var s = start()
        s = SessionEngine.pause(s, clock(20_000), PauseReason.USER)
        assertEquals(20_000, SessionEngine.evaluate(s, t0 + 80_000).positionMs)
        s = SessionEngine.resume(s, clock(80_000))
        val st = SessionEngine.evaluate(s, t0 + 85_000)
        assertEquals(25_000, st.positionMs)
        assertEquals(1, st.taskIndex)
    }

    @Test
    fun `flexible mode - early confirmation starts next task immediately`() {
        var s = start(ScheduleMode.FLEXIBLE)
        s = SessionEngine.confirm(s, clock(15_000)) // zadanie 0 (10–25 s) potwierdzone po 5 s
        val st = SessionEngine.evaluate(s, t0 + 15_000)
        assertEquals(1, st.taskIndex)
        assertEquals(Phase.ACTIVE, st.phase)
        assertEquals(15_000, st.effectiveStartsMs[1])
        assertEquals(30_000, st.effectiveEndsMs[1])
    }

    @Test
    fun `flexible mode - waits for confirmation only up to the timeout`() {
        val s = start(ScheduleMode.FLEXIBLE, timeoutSec = 30)
        val waiting = SessionEngine.evaluate(s, t0 + 40_000)
        assertEquals(Phase.AWAITING_CONFIRMATION, waiting.phase)
        assertEquals(0, waiting.taskIndex)
        val after = SessionEngine.evaluate(s, t0 + 55_000)
        assertEquals(Phase.ACTIVE, after.phase)
        assertEquals(1, after.taskIndex)
        assertEquals(55_000, after.effectiveStartsMs[1])
    }

    @Test
    fun `flexible mode - no response at all cannot hang the routine`() {
        val s = start(ScheduleMode.FLEXIBLE, timeoutSec = 30)
        // 5 zadań × 30 s maksymalnego czekania = najpóźniej 310 + 150 s.
        assertEquals(Phase.FINISHED, SessionEngine.evaluate(s, t0 + 460_000).phase)
        assertFalse(SessionEngine.evaluate(s, t0 + 459_000).phase == Phase.FINISHED)
    }

    @Test
    fun `flexible confirmation during waiting window`() {
        var s = start(ScheduleMode.FLEXIBLE, timeoutSec = 30)
        s = SessionEngine.confirm(s, clock(30_000)) // 5 s po planowanym końcu zadania 0
        val st = SessionEngine.evaluate(s, t0 + 30_000)
        assertEquals(1, st.taskIndex)
        assertEquals(listOf(5_000L), s.completedShifts)
        assertEquals(45_000, st.effectiveEndsMs[1])
    }

    @Test
    fun `recovery - continue schedule keeps original timing`() {
        var s = start(behavior = InterruptionBehavior.CONTINUE_SCHEDULE)
        s = SessionEngine.heartbeat(s, clock(20_000))
        val rec = SessionEngine.recover(s, clock(60_000))
        assertTrue(rec.interrupted)
        val st = SessionEngine.evaluate(rec.snapshot, t0 + 60_000)
        assertEquals(60_000, st.positionMs)
        assertEquals(2, st.taskIndex)
    }

    @Test
    fun `recovery - pause behaviour pauses at the moment of interruption`() {
        var s = start(behavior = InterruptionBehavior.PAUSE)
        s = SessionEngine.heartbeat(s, clock(20_000))
        val rec = SessionEngine.recover(s, clock(60_000)).snapshot
        assertTrue(rec.paused)
        assertEquals(PauseReason.INTERRUPTED, rec.pauseReason)
        assertEquals(20_000, SessionEngine.evaluate(rec, t0 + 90_000).positionMs)
    }

    @Test
    fun `recovery - resume current task restarts the interrupted task`() {
        var s = start(behavior = InterruptionBehavior.RESUME_CURRENT_TASK)
        s = s.copy(firedCues = setOf(CueKeys.taskStart(0)))
        s = SessionEngine.heartbeat(s, clock(20_000))
        val rec = SessionEngine.recover(s, clock(60_000)).snapshot
        val st = SessionEngine.evaluate(rec, t0 + 60_000)
        assertEquals(0, st.taskIndex)
        assertEquals(10_000, st.positionMs)
        assertFalse(CueKeys.taskStart(0) in rec.firedCues) // zadanie zostanie zapowiedziane ponownie
    }

    @Test
    fun `short gaps are not treated as interruptions`() {
        var s = start(behavior = InterruptionBehavior.PAUSE)
        s = SessionEngine.heartbeat(s, clock(20_000))
        val rec = SessionEngine.recover(s, clock(22_000))
        assertFalse(rec.interrupted)
        assertFalse(rec.snapshot.paused)
    }

    @Test
    fun `recovery after reboot uses wall clock`() {
        var s = start()
        s = SessionEngine.heartbeat(s, clock(20_000))
        // Po restarcie elapsedRealtime liczy od zera, inny licznik uruchomień.
        val afterReboot = ClockReading(elapsed = 3_000, wall = 1_700_000_000_000L + 50_000, bootCount = 2)
        val rec = SessionEngine.recover(s, afterReboot)
        assertTrue(rec.interrupted)
        val st = SessionEngine.evaluate(rec.snapshot, 3_000)
        assertEquals(50_000, st.positionMs)
    }

    @Test
    fun `phone call - continue behaviour does not move the schedule`() {
        var s = start(behavior = InterruptionBehavior.CONTINUE_SCHEDULE)
        s = SessionEngine.callStarted(s, clock(12_000))
        assertTrue(s.callActive)
        assertFalse(s.paused)
        s = SessionEngine.callEnded(s, clock(50_000))
        assertEquals(2, SessionEngine.evaluate(s, t0 + 50_000).taskIndex)
    }

    @Test
    fun `phone call - pause behaviour stays paused after the call`() {
        var s = start(behavior = InterruptionBehavior.PAUSE)
        s = SessionEngine.callStarted(s, clock(12_000))
        s = SessionEngine.callEnded(s, clock(50_000))
        assertTrue(s.paused)
        assertEquals(12_000, SessionEngine.evaluate(s, t0 + 70_000).positionMs)
    }

    @Test
    fun `phone call - resume current task restarts the task after the call`() {
        var s = start(behavior = InterruptionBehavior.RESUME_CURRENT_TASK)
        s = SessionEngine.callStarted(s, clock(30_000)) // zadanie 1 (25–40 s)
        s = SessionEngine.callEnded(s, clock(90_000))
        assertFalse(s.paused)
        val st = SessionEngine.evaluate(s, t0 + 90_000)
        assertEquals(1, st.taskIndex)
        assertEquals(25_000, st.positionMs)
    }

    @Test
    fun `snapshot survives serialization`() {
        var s = start(ScheduleMode.FLEXIBLE)
        s = SessionEngine.confirm(s, clock(15_000)).copy(firedCues = setOf("start:0"))
        val decoded = SessionJson.decodeOrNull(SessionJson.encode(s))
        assertEquals(s, decoded)
        assertNull(SessionJson.decodeOrNull("{not json"))
    }
}
