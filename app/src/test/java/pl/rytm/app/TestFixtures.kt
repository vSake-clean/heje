package pl.rytm.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.ClockReading
import pl.rytm.app.domain.session.MusicOutput
import pl.rytm.app.domain.session.MusicState
import pl.rytm.app.domain.session.MusicStatus
import pl.rytm.app.domain.session.SessionClock
import pl.rytm.app.domain.session.SessionPersistence
import pl.rytm.app.domain.session.SessionPlan
import pl.rytm.app.domain.session.SessionSnapshot
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.domain.session.SpeechListener
import pl.rytm.app.domain.session.SpeechOutput

/** Przykład z założeń projektu: 00:10 wstań, 00:25 biurko, 00:40 ubrania, 01:40 łazienka, 02:10–05:10 higiena. */
fun specMorningTasks() = listOf(
    RoutineTask(title = "Wstań z łóżka", spokenText = "Wstań z łóżka.", durationSec = 15, gapBeforeSec = 10),
    RoutineTask(title = "Podejdź do biurka", spokenText = "Podejdź do biurka.", durationSec = 15),
    RoutineTask(title = "Przygotuj ubrania", spokenText = "Przygotuj ubrania.", durationSec = 60),
    RoutineTask(title = "Idź do łazienki", spokenText = "Idź do łazienki.", durationSec = 30),
    RoutineTask(title = "Poranna higiena", spokenText = "Rozpocznij poranną higienę.", durationSec = 180),
)

fun routineOf(
    tasks: List<RoutineTask> = specMorningTasks(),
    mode: ScheduleMode = ScheduleMode.STRICT,
    flexTimeoutSec: Int = 30,
    interruption: InterruptionBehavior = InterruptionBehavior.CONTINUE_SCHEDULE,
    announcements: AnnouncementConfig = AnnouncementConfig(endMessage = "Rutyna zakończona."),
    music: Boolean = true,
) = Routine(
    id = 1,
    name = "Test",
    tasks = tasks,
    mode = mode,
    flexibleTimeoutSec = flexTimeoutSec,
    interruptionBehavior = interruption,
    announcements = announcements,
    music = if (music) MusicSelection("https://soundcloud.com/forss/flickermood", "Flickermood", "Forss", null, MusicKind.TRACK) else null,
    musicPlayback = if (music) MusicPlayback.EMBEDDED else MusicPlayback.NONE,
)

fun planOf(routine: Routine = routineOf()) = SessionPlan.from(routine)

/** Zegar testowy: czas wirtualny + możliwość „skoku” (symulacja uśpienia/zamrożenia procesu). */
class TestClock(private val virtualNow: () -> Long, var bootCount: Int = 1) : SessionClock {
    var jumpMs = 0L
    val wallBase = 1_700_000_000_000L
    val elapsed: Long get() = virtualNow() + jumpMs + 1_000_000L
    override fun now() = ClockReading(elapsed, wallBase + virtualNow() + jumpMs, bootCount)
}

/** Sztuczna synteza mowy: odtwarza „wypowiedź” przez czas zależny od tekstu, rejestruje przebieg. */
class FakeSpeech(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val startLatencyMs: Long = 0,
    private val durationOf: (String) -> Long = { 1_000L + it.length * 40L },
) : SpeechOutput {
    data class Record(val id: String, val text: String, val requestedAt: Long, var startedAt: Long? = null, var endedAt: Long? = null, var interrupted: Boolean = false)

    override var availability: SpeechAvailability = SpeechAvailability.READY
    override var listener: SpeechListener? = null
    val log = mutableListOf<Record>()
    private var current: Record? = null
    private var job: Job? = null

    override fun speak(id: String, text: String, flush: Boolean) {
        if (flush) stopCurrent()
        val r = Record(id, text, clock())
        log += r
        current = r
        job = scope.launch {
            delay(startLatencyMs)
            r.startedAt = clock()
            listener?.onStart(id)
            delay(durationOf(text))
            r.endedAt = clock()
            current = null
            listener?.onDone(id)
        }
    }

    override fun stop() = stopCurrent()

    private fun stopCurrent() {
        val r = current ?: return
        job?.cancel()
        if (r.startedAt == null) r.startedAt = clock()
        r.endedAt = clock()
        r.interrupted = true
        current = null
        listener?.onDone(r.id)
    }

    val texts: List<String> get() = log.map { it.text }
}

/** Sztuczny odtwarzacz: zapisuje wszystkie zmiany głośności z czasem. */
class FakeMusic(private val clock: () -> Long) : MusicOutput {
    private val _state = MutableStateFlow(MusicState(playback = MusicPlayback.EMBEDDED, volumeControllable = true))
    override val state: StateFlow<MusicState> = _state
    val volumes = mutableListOf<Pair<Long, Float>>()
    var plays = 0
    var pauses = 0
    var stopped = false

    override fun prepare(plan: SessionPlan, autoPlay: Boolean) {
        _state.value = _state.value.copy(status = if (autoPlay) MusicStatus.PLAYING else MusicStatus.PAUSED, title = plan.music?.title)
        if (autoPlay) plays++
    }

    override fun play() {
        plays++
        _state.value = _state.value.copy(status = MusicStatus.PLAYING)
    }

    override fun pause() {
        pauses++
        _state.value = _state.value.copy(status = MusicStatus.PAUSED)
    }

    override fun setVolume(volume: Float) {
        volumes += clock() to volume
    }

    override fun stop() {
        stopped = true
        _state.value = _state.value.copy(status = MusicStatus.NONE)
    }

    fun simulate(status: MusicStatus) {
        _state.value = _state.value.copy(status = status)
    }

    /** Głośność obowiązująca w chwili [t]. */
    fun volumeAt(t: Long): Float = volumes.lastOrNull { it.first <= t }?.second ?: 100f
}

class MemoryPersistence : SessionPersistence {
    var saved: SessionSnapshot? = null
    var saves = 0
    var cleared = false
    override fun save(snapshot: SessionSnapshot, sync: Boolean) {
        saved = snapshot
        saves++
    }

    override fun clear() {
        cleared = true
        saved = null
    }
}
