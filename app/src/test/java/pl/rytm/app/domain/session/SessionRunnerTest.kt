package pl.rytm.app.domain.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.FakeMusic
import pl.rytm.app.FakeSpeech
import pl.rytm.app.MemoryPersistence
import pl.rytm.app.TestClock
import pl.rytm.app.domain.audio.DuckingController
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.planOf
import pl.rytm.app.routineOf
import kotlin.math.abs

/**
 * Testy integracyjne prowadzenia sesji na wirtualnym zegarze: głos, harmonogram, muzyka, przerwania.
 * Czas „0” sesji odpowiada [start]. Wszystkie asercje czasu są w milisekundach osi sesji.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRunnerTest {

    private class Harness(
        val scope: TestScope,
        plan: SessionPlan,
        latencyCompensation: Boolean = false,
        speechLatency: Long = 0,
        speechDuration: ((String) -> Long)? = null,
        withMusic: Boolean = true,
    ) {
        val clock = TestClock({ scope.currentTime })
        val start = clock.elapsed
        val speech = if (speechDuration != null) {
            FakeSpeech(scope.backgroundScope, { clock.elapsed - start }, speechLatency, speechDuration)
        } else {
            FakeSpeech(scope.backgroundScope, { clock.elapsed - start }, speechLatency)
        }
        val music = FakeMusic { clock.elapsed - start }
        val persistence = MemoryPersistence()
        var ended = false
        var endedByUser = false
        val taskChanges = mutableListOf<Pair<Long, Int?>>()
        val effects = object : SessionEffects {
            override fun onTaskChanged(live: LiveSession) {
                taskChanges += live.status.positionMs to live.status.taskIndex
            }

            override fun onEnded(live: LiveSession?, stoppedByUser: Boolean) {
                ended = true
                endedByUser = stoppedByUser
            }
        }
        val runner = SessionRunner(
            scope = scope.backgroundScope,
            clock = clock,
            speech = speech,
            music = if (withMusic) music else null,
            persistence = persistence,
            effects = effects,
            estimator = HeuristicSpeechEstimator(),
            ducking = DuckingController(),
            config = RunnerConfig(latencyCompensation = latencyCompensation),
        )
        val snapshot = SessionEngine.start("test", plan, clock.now())

        fun startRunner() {
            runner.start(snapshot)
            scope.runCurrent()
        }

        /** Przesuwa czas do pozycji [ms] osi sesji. */
        fun advanceTo(ms: Long) {
            val target = start + ms
            val delta = target - clock.elapsed
            if (delta > 0) scope.advanceTimeBy(delta)
            scope.runCurrent()
        }

        fun spokenAt(text: String) = speech.log.firstOrNull { it.text == text }?.requestedAt
    }

    @Test
    fun `strict routine - commands are spoken exactly on schedule`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(320_000)
        assertEquals(10_000L, h.spokenAt("Wstań z łóżka."))
        assertEquals(25_000L, h.spokenAt("Podejdź do biurka."))
        assertEquals(40_000L, h.spokenAt("Przygotuj ubrania."))
        assertEquals(100_000L, h.spokenAt("Idź do łazienki."))
        assertEquals(130_000L, h.spokenAt("Rozpocznij poranną higienę."))
        assertEquals(310_000L, h.spokenAt("Rutyna zakończona."))
        assertTrue(h.ended)
        assertFalse(h.endedByUser)
        assertTrue(h.persistence.cleared)
    }

    @Test
    fun `no accidental delays - task changes happen exactly at boundaries`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(320_000)
        val activeChanges = h.taskChanges.filter { it.second != null }.map { it.first }.distinct()
        assertTrue(activeChanges.containsAll(listOf(10_000L, 25_000L, 40_000L, 100_000L, 130_000L)))
    }

    @Test
    fun `user who did not finish a task still gets the next command on time (strict)`() = runTest {
        // Ścielenie łóżka 15 s – po 15 s od razu kolejne polecenie, bez czekania na potwierdzenie.
        val tasks = listOf(
            RoutineTask(title = "Pościel łóżko", spokenText = "Pościel łóżko.", durationSec = 15),
            RoutineTask(title = "Biurko", spokenText = "Podejdź do biurka.", durationSec = 15),
        )
        val h = Harness(this, planOf(routineOf(tasks = tasks)))
        h.startRunner()
        h.advanceTo(40_000)
        assertEquals(0L, h.spokenAt("Pościel łóżko."))
        assertEquals(15_000L, h.spokenAt("Podejdź do biurka."))
    }

    @Test
    fun `latency compensation starts speech earlier so audio begins on time`() = runTest {
        val h = Harness(this, planOf(), latencyCompensation = true, speechLatency = 300)
        h.startRunner()
        h.advanceTo(200_000)
        // Po kilku pomiarach opóźnienia dźwięk (startedAt) zaczyna się blisko planowanej chwili.
        val r = h.speech.log.first { it.text == "Rozpocznij poranną higienę." }
        assertTrue("start ${r.startedAt}", abs(r.startedAt!! - 130_000L) <= 150)
    }

    @Test
    fun `voice messages never overlap`() = runTest {
        // Bardzo długie wypowiedzi (12 s) w zadaniach po 15 s i krótszych.
        val tasks = (0 until 6).map { RoutineTask(title = "Z$it", spokenText = "Polecenie $it", durationSec = if (it % 2 == 0) 5 else 15) }
        val h = Harness(this, planOf(routineOf(tasks = tasks)), speechDuration = { 12_000L })
        h.startRunner()
        h.advanceTo(80_000)
        val log = h.speech.log
        assertTrue(log.size >= 6)
        log.zipWithNext().forEach { (a, b) ->
            assertNotNull(a.endedAt)
            assertTrue("${a.text} kończy się ${a.endedAt}, ${b.text} zaczyna ${b.startedAt}", b.startedAt!! >= a.endedAt!!)
        }
    }

    @Test
    fun `long message is cut by the next command instead of delaying the schedule`() = runTest {
        val tasks = listOf(
            RoutineTask(title = "A", spokenText = "Bardzo długi komunikat", durationSec = 3),
            RoutineTask(title = "B", spokenText = "Następne.", durationSec = 10),
        )
        val h = Harness(this, planOf(routineOf(tasks = tasks)), speechDuration = { if (it.startsWith("Bardzo")) 8_000L else 800L })
        h.startRunner()
        h.advanceTo(20_000)
        val first = h.speech.log.first()
        assertTrue(first.interrupted)
        assertEquals(3_000L, first.endedAt)
        assertEquals(3_000L, h.spokenAt("Następne."))
    }

    @Test
    fun `music is ducked gently during speech and restored smoothly`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(20_000)
        val m = h.music
        // Przed komunikatem: pełna głośność; w trakcie: 75%; potem powrót do 100%.
        assertEquals(100f, m.volumeAt(9_000), 0.5f)
        assertEquals(75f, m.volumeAt(10_200), 0.5f)
        assertTrue(m.volumes.all { it.second >= 75f - 0.01f }) // nigdy nie ciszej niż 75% – muzyka gra dalej
        assertEquals(100f, m.volumeAt(19_000), 0.01f)
        // Płynność: brak skoków większych niż kilka procent między kolejnymi krokami.
        val window = m.volumes.filter { it.first in 9_000..16_000 }
        window.zipWithNext().forEach { (a, b) -> assertTrue("${a} → ${b}", abs(b.second - a.second) <= 6f) }
        assertEquals(0, m.pauses) // muzyka nie jest zatrzymywana podczas mowy
    }

    @Test
    fun `ducking starts before the command so voice begins on already lowered music`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(12_000)
        assertTrue(h.music.volumeAt(9_800) < 100f)
        assertEquals(75f, h.music.volumeAt(10_000), 0.5f)
    }

    @Test
    fun `close announcements do not restore volume in between`() = runTest {
        val tasks = listOf(
            RoutineTask(title = "A", spokenText = "Raz.", durationSec = 2),
            RoutineTask(title = "B", spokenText = "Dwa.", durationSec = 2),
            RoutineTask(title = "C", spokenText = "Trzy.", durationSec = 10),
        )
        val h = Harness(this, planOf(routineOf(tasks = tasks)), speechDuration = { 900L })
        h.startRunner()
        h.advanceTo(15_000)
        val between = h.music.volumes.filter { it.first in 500..4_500 }
        assertTrue(between.all { it.second <= 75.5f })
        // Po ostatnim poleceniu (4 s) i przytrzymaniu muzyka wraca do 100% (komunikat końcowy dopiero o 14 s).
        assertEquals(100f, h.music.volumeAt(12_000), 0.01f)
    }

    @Test
    fun `screen off - delayed loop still announces only the current task`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(12_000)
        // Symulacja zamrożenia procesu/uśpienia CPU: zegar skacze o 90 s bez wywołań pętli.
        h.clock.jumpMs += 90_000
        h.advanceTo(102_500 + 0) // pozycja ≈ 102,5 s → zadanie 3 (łazienka)
        val texts = h.speech.texts
        assertTrue(texts.contains("Teraz: Idź do łazienki."))
        assertFalse(texts.contains("Podejdź do biurka."))
        assertFalse(texts.contains("Przygotuj ubrania."))
        // Harmonogram się nie rozjechał: następne polecenie dokładnie o 130 s.
        h.advanceTo(140_000)
        assertEquals(130_000L, h.spokenAt("Rozpocznij poranną higienę."))
    }

    @Test
    fun `losing internet does not affect the schedule`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(11_000)
        h.music.simulate(MusicStatus.WAITING_FOR_NETWORK)
        h.advanceTo(45_000)
        assertEquals(25_000L, h.spokenAt("Podejdź do biurka."))
        assertEquals(40_000L, h.spokenAt("Przygotuj ubrania."))
        assertEquals(MusicStatus.WAITING_FOR_NETWORK, h.runner.live.value?.music?.status)
    }

    @Test
    fun `music paused or interrupted does not affect the schedule`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.music.simulate(MusicStatus.INTERRUPTED)
        h.advanceTo(45_000)
        assertEquals(10_000L, h.spokenAt("Wstań z łóżka."))
        assertEquals(40_000L, h.spokenAt("Przygotuj ubrania."))
    }

    @Test
    fun `routine works without music`() = runTest {
        val h = Harness(this, planOf(routineOf(music = false)), withMusic = false)
        h.startRunner()
        h.advanceTo(30_000)
        assertEquals(25_000L, h.spokenAt("Podejdź do biurka."))
    }

    @Test
    fun `pause and resume - music follows and current task is re-announced`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(12_000)
        h.runner.pause()
        h.scope.runCurrent()
        assertEquals(1, h.music.pauses)
        h.advanceTo(60_000)
        assertEquals(1, h.speech.log.size) // w pauzie cisza
        h.runner.resume()
        h.scope.runCurrent()
        assertEquals("Teraz: Wstań z łóżka.", h.speech.log.last().text)
        // Pozostało 13 s zadania 0 → następne polecenie o 60 + 13 = 73 s czasu rzeczywistego.
        h.advanceTo(80_000)
        assertEquals(73_000L, h.spokenAt("Podejdź do biurka."))
    }

    @Test
    fun `flexible mode - confirming early moves to next task immediately`() = runTest {
        val h = Harness(this, planOf(routineOf(mode = ScheduleMode.FLEXIBLE, flexTimeoutSec = 30)))
        h.startRunner()
        h.advanceTo(14_000)
        h.runner.confirm()
        h.scope.runCurrent()
        assertEquals(14_000L, h.spokenAt("Podejdź do biurka."))
    }

    @Test
    fun `flexible mode - without confirmation waits at most the timeout`() = runTest {
        val h = Harness(this, planOf(routineOf(mode = ScheduleMode.FLEXIBLE, flexTimeoutSec = 30)))
        h.startRunner()
        h.advanceTo(60_000)
        assertEquals(25_000L, h.spokenAt(pl.rytm.app.domain.speech.AnnouncementTexts.AWAITING_CONFIRMATION))
        assertEquals(55_000L, h.spokenAt("Podejdź do biurka."))
    }

    @Test
    fun `phone call with continue behaviour - silent during call, then current task`() = runTest {
        val h = Harness(this, planOf(routineOf(interruption = InterruptionBehavior.CONTINUE_SCHEDULE)))
        h.startRunner()
        h.advanceTo(20_000)
        h.runner.onCallStateChanged(true)
        h.advanceTo(50_000)
        assertFalse(h.speech.texts.any { it.contains("biurka") })
        h.runner.onCallStateChanged(false)
        h.scope.runCurrent()
        assertEquals("Teraz: Przygotuj ubrania.", h.speech.log.last().text)
        assertTrue(h.music.pauses >= 1 && h.music.plays >= 2)
    }

    @Test
    fun `phone call with resume-current behaviour restarts the task after the call`() = runTest {
        val h = Harness(this, planOf(routineOf(interruption = InterruptionBehavior.RESUME_CURRENT_TASK)))
        h.startRunner()
        h.advanceTo(30_000) // zadanie 1 (25–40)
        h.runner.onCallStateChanged(true)
        h.advanceTo(90_000)
        h.runner.onCallStateChanged(false)
        h.scope.runCurrent()
        assertEquals("Teraz: Podejdź do biurka.", h.speech.log.last().text)
        // Zadanie 1 trwa znowu 15 s → kolejne o 105 s.
        h.advanceTo(110_000)
        assertEquals(105_000L, h.spokenAt("Przygotuj ubrania."))
    }

    @Test
    fun `recovery after process death announces current task and keeps timing`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(30_000)
        val saved = h.persistence.saved!!
        // „Zabicie procesu”: nowy runner startuje z zapisanej migawki 40 s później.
        h.advanceTo(70_000)
        val rec = SessionEngine.recover(saved, h.clock.now())
        assertTrue(rec.interrupted)
        val speech2 = FakeSpeech(backgroundScope, { h.clock.elapsed - h.start })
        val runner2 = SessionRunner(
            backgroundScope, h.clock, speech2, FakeMusic { 0 }, MemoryPersistence(), object : SessionEffects {},
            HeuristicSpeechEstimator(), DuckingController(), RunnerConfig(latencyCompensation = false),
        )
        runner2.start(rec.snapshot, announceCurrent = true)
        runCurrent()
        assertEquals("Teraz: Przygotuj ubrania.", speech2.log.first().text)
        h.advanceTo(105_000)
        assertEquals(100_000L, speech2.log.first { it.text == "Idź do łazienki." }.requestedAt)
    }

    @Test
    fun `speech unavailable - schedule still runs and session ends`() = runTest {
        val h = Harness(this, planOf())
        h.speech.availability = SpeechAvailability.UNAVAILABLE
        h.startRunner()
        h.advanceTo(320_000)
        assertTrue(h.speech.log.isEmpty())
        assertTrue(h.ended)
        assertEquals(Phase.FINISHED, h.runner.live.value?.status?.phase)
    }

    @Test
    fun `speech initializing late - first command spoken as soon as engine is ready`() = runTest {
        val h = Harness(this, planOf())
        h.speech.availability = SpeechAvailability.INITIALIZING
        h.startRunner()
        h.advanceTo(10_400)
        assertTrue(h.speech.log.isEmpty())
        h.speech.availability = SpeechAvailability.READY
        h.advanceTo(11_000)
        assertEquals("Wstań z łóżka.", h.speech.log.first().text)
    }

    @Test
    fun `stop ends the session and clears saved state`() = runTest {
        val h = Harness(this, planOf())
        h.startRunner()
        h.advanceTo(12_000)
        h.runner.stop()
        h.advanceTo(20_000)
        assertTrue(h.ended)
        assertTrue(h.endedByUser)
        assertTrue(h.music.stopped)
        assertTrue(h.persistence.cleared)
    }

    @Test
    fun `announcements disabled - silent but schedule and end work`() = runTest {
        val h = Harness(this, planOf(routineOf(announcements = AnnouncementConfig(enabled = false))))
        h.startRunner()
        h.advanceTo(320_000)
        assertTrue(h.speech.log.isEmpty())
        assertTrue(h.ended)
        // Bez komunikatów nie ma przyciszania (poza wyciszeniem muzyki po zakończeniu rutyny).
        assertTrue(h.music.volumes.filter { it.first < 310_000 }.all { abs(it.second - 100f) < 0.01f })
    }
}
