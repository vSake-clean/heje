package pl.rytm.app.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.planOf
import pl.rytm.app.routineOf

class AnnouncementSchedulerTest {

    private val scheduler = AnnouncementScheduler(HeuristicSpeechEstimator())
    private val t0 = 1_000L

    private fun snap(plan: SessionPlan) = SessionEngine.start("s", plan, ClockReading(t0, 0, 1))

    @Test
    fun `task cues are planned exactly at task starts`() {
        val plan = planOf()
        val s = snap(plan)
        val cues = scheduler.cues(plan, SessionEngine.evaluate(s, t0)).filter { it.type == CueType.TASK_START }
        assertEquals(listOf(10_000L, 25_000L, 40_000L, 100_000L, 130_000L), cues.map { it.timeMs })
        assertEquals("Wstań z łóżka.", cues[0].text)
    }

    @Test
    fun `nothing is due before the first task and the first command is due at 10 s`() {
        val plan = planOf()
        val s = snap(plan)
        assertNull(scheduler.due(plan, SessionEngine.evaluate(s, t0 + 9_999), emptySet()).utterance)
        val due = scheduler.due(plan, SessionEngine.evaluate(s, t0 + 10_000), emptySet())
        assertEquals("Wstań z łóżka.", due.utterance?.text)
    }

    @Test
    fun `after a long gap only the current task is announced, with Teraz prefix`() {
        val plan = planOf()
        val s = snap(plan)
        val due = scheduler.due(plan, SessionEngine.evaluate(s, t0 + 105_000), emptySet())
        assertEquals("Teraz: Idź do łazienki.", due.utterance?.text)
        // Wcześniejsze polecenia oznaczone jako obsłużone – nie zostaną wypowiedziane później.
        assertTrue(due.handledKeys.containsAll(listOf("start:0", "start:1", "start:2", "start:3")))
    }

    @Test
    fun `warnings are planned before task end only when they fit`() {
        val plan = planOf(routineOf(announcements = AnnouncementConfig(warningBeforeEndSec = 10)))
        val st = SessionEngine.evaluate(snap(plan), t0)
        val warnings = scheduler.cues(plan, st).filter { it.type == CueType.WARNING }
        // 15-sekundowe zadania są za krótkie na ostrzeżenie (musi trwać co najmniej 2 × 10 s).
        assertEquals(listOf(90_000L, 120_000L, 300_000L), warnings.map { it.timeMs })
        assertEquals("Zostało dziesięć sekund.", warnings[0].text)
    }

    @Test
    fun `finish-at-start timing speaks ahead so the command ends when the task starts`() {
        val plan = planOf().copy(announceTiming = AnnounceTiming.FINISH_AT_TASK_START)
        val st = SessionEngine.evaluate(snap(plan), t0)
        val cue = scheduler.cues(plan, st).first { it.key == CueKeys.taskStart(2) }
        val speech = HeuristicSpeechEstimator().estimateMs("Przygotuj ubrania.")
        assertEquals(40_000L - speech, cue.timeMs)
        // Zapowiedź z wyprzedzeniem jest wypowiadana, gdy poprzednie zadanie jeszcze trwa.
        val due = scheduler.due(plan, SessionEngine.evaluate(snap(plan), t0 + cue.timeMs), setOf("start:0", "start:1"))
        assertEquals("Przygotuj ubrania.", due.utterance?.text)
    }

    @Test
    fun `flexible waiting does not consume the next task's command`() {
        val plan = planOf(routineOf(mode = ScheduleMode.FLEXIBLE, flexTimeoutSec = 30))
        val s = snap(plan)
        val fired = setOf("start:0")
        val waiting = SessionEngine.evaluate(s, t0 + 25_500)
        assertEquals(Phase.AWAITING_CONFIRMATION, waiting.phase)
        val due = scheduler.due(plan, waiting, fired)
        assertEquals(AnnouncementTextsCheck.AWAITING, due.utterance?.text)
        assertTrue("start:1" !in due.handledKeys)
        // Po upływie limitu zadanie 1 startuje i jego polecenie pada.
        val after = SessionEngine.evaluate(s, t0 + 55_000)
        assertEquals("Podejdź do biurka.", scheduler.due(plan, after, fired + due.handledKeys).utterance?.text)
    }

    @Test
    fun `end message only when finished`() {
        val plan = planOf()
        val s = snap(plan)
        val fired = (0..4).map { "start:$it" }.toSet()
        assertNull(scheduler.due(plan, SessionEngine.evaluate(s, t0 + 309_000), fired).utterance)
        val end = scheduler.due(plan, SessionEngine.evaluate(s, t0 + 310_000), fired)
        assertNotNull(end.utterance)
        assertEquals("Rutyna zakończona.", end.utterance!!.text)
    }

    @Test
    fun `paused session announces nothing`() {
        val plan = planOf()
        val s = SessionEngine.pause(snap(plan), ClockReading(t0 + 10_500, 0, 1), PauseReason.USER)
        assertNull(scheduler.due(plan, SessionEngine.evaluate(s, t0 + 60_000), emptySet()).utterance)
    }
}

private object AnnouncementTextsCheck {
    const val AWAITING = pl.rytm.app.domain.speech.AnnouncementTexts.AWAITING_CONFIRMATION
}
