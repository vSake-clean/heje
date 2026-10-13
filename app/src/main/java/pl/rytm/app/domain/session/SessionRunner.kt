package pl.rytm.app.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.rytm.app.domain.audio.DuckingController
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.speech.SpeechDurationEstimator
import kotlin.math.abs

data class RunnerConfig(
    /** Maksymalny odstęp między krokami pętli (UI/powiadomienia). Komunikaty mają własne, dokładne wybudzenia. */
    val tickMs: Long = 250,
    val heartbeatMs: Long = 2_000,
    val volumeStepMs: Long = 30,
    /** Kompensacja opóźnienia syntezy mowy (czas od speak() do faktycznego dźwięku). */
    val latencyCompensation: Boolean = true,
    val initialLatencyMs: Long = 150,
    val maxLatencyMs: Long = 600,
    /** Przyciszanie muzyki w odtwarzaczu wbudowanym. */
    val duckingEnabled: Boolean = true,
    val fadeOutMs: Long = 2_000,
    /** Maksymalny czas czekania na dokończenie komunikatu końcowego. */
    val endSpeechTimeoutMs: Long = 10_000,
    /** Zabezpieczenie: wypowiedź bez zdarzenia „koniec” po tym czasie uznajemy za zakończoną. */
    val speechWatchdogExtraMs: Long = 5_000,
)

/**
 * Prowadzi sesję w czasie rzeczywistym: wylicza stan z zegara monotonicznego, wypowiada komunikaty
 * dokładnie o zaplanowanych porach, przycisza muzykę na czas mowy, obsługuje pauzy i przerwania.
 *
 * Nie zależy od Androida ani od interfejsu – działa w usłudze pierwszoplanowej.
 */
class SessionRunner(
    private val scope: CoroutineScope,
    private val clock: SessionClock,
    private val speech: SpeechOutput,
    private val music: MusicOutput?,
    private val persistence: SessionPersistence,
    private val effects: SessionEffects,
    estimator: SpeechDurationEstimator,
    val ducking: DuckingController,
    private val config: RunnerConfig = RunnerConfig(),
) : SpeechListener {

    private val scheduler = AnnouncementScheduler(estimator)
    private val estimator = estimator
    private val lock = Any()

    private var snapshot: SessionSnapshot? = null
    private var notice: String? = null
    private var loopJob: Job? = null
    private var volumeJob: Job? = null
    private var lastTaskKey: Pair<Phase, Int?>? = null
    private var lastPersistElapsed = 0L
    private var persistedCopy: SessionSnapshot? = null

    // Mowa
    private var currentUtteranceId: String? = null
    private var currentUtteranceStartedAt = 0L
    private var currentUtteranceDeadline = 0L
    private val speakCalledAt = HashMap<String, Long>()
    private var utteranceCounter = 0
    private var latencyMs = config.initialLatencyMs
    private var duckHeld = false
    private var duckHeldSince = 0L
    private var preDuckedKey: String? = null

    /** Polecenia powtarzane po wznowieniu / rozmowie / odtworzeniu – dostają przedrostek „Teraz:”. */
    private val reannounceKeys = HashSet<String>()

    // Zakończenie
    private var finishedAtElapsed: Long? = null
    private var fadeStartElapsed: Long? = null
    private var ended = false

    /** Historia wypowiedzi (do diagnostyki i testów): czas ścienny monotoniczny, pozycja osi, tekst. */
    val spokenLog = mutableListOf<SpokenRecord>()

    data class SpokenRecord(val elapsed: Long, val positionMs: Long, val text: String, val keys: List<String>)

    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    val isRunning: Boolean get() = loopJob?.isActive == true

    val currentSnapshot: SessionSnapshot? get() = synchronized(lock) { snapshot }

    /**
     * Uruchamia pętlę dla nowej lub odtworzonej sesji.
     * @param announceCurrent po odtworzeniu – powtórz polecenie bieżącego zadania („Teraz: …”).
     */
    fun start(initial: SessionSnapshot, announceCurrent: Boolean = false, notice: String? = null) {
        synchronized(lock) {
            var s = initial
            if (announceCurrent) {
                val st = SessionEngine.evaluate(s, clock.now().elapsed)
                st.taskIndex?.let { idx ->
                    if (st.phase == Phase.ACTIVE || st.phase == Phase.AWAITING_CONFIRMATION) {
                        s = s.copy(firedCues = s.firedCues - CueKeys.taskStart(idx))
                        reannounceKeys += CueKeys.taskStart(idx)
                    }
                }
            }
            snapshot = s
            this.notice = notice
            ended = false
            finishedAtElapsed = null
            fadeStartElapsed = null
            lastTaskKey = null
            persist(s, sync = true)
        }
        speech.listener = this
        val s = currentSnapshot!!
        music?.let { m ->
            if (s.plan.musicPlayback != MusicPlayback.NONE) {
                m.prepare(s.plan, autoPlay = !s.paused && !s.callActive)
            }
        }
        loopJob?.cancel()
        loopJob = scope.launch { loop() }
        volumeJob?.cancel()
        volumeJob = scope.launch { volumeLoop() }
    }

    // ---------------------------------------------------------------- polecenia

    fun pause() = mutate(sync = true) { s, c ->
        if (s.paused) return@mutate s
        stopSpeech(c.elapsed)
        music?.pause()
        notice = null
        SessionEngine.pause(s, c, PauseReason.USER)
    }

    fun resume() = mutate(sync = true) { s, c ->
        if (!s.paused) return@mutate s
        var r = if (s.pauseReason == PauseReason.CALL && s.interruptedTaskIndex != null) {
            SessionEngine.resumeFromTaskStart(s, c, s.interruptedTaskIndex)
        } else {
            SessionEngine.resume(s, c)
        }
        // Po wznowieniu przypomnij, co teraz robimy.
        r = withReannouncement(r, c)
        if (!r.callActive) music?.play()
        notice = null
        r
    }

    /** Tryb elastyczny: „Zrobione”. */
    fun confirm() = mutate(sync = true) { s, c -> SessionEngine.confirm(s, c) }

    fun stop() {
        val c = clock.now()
        val last: LiveSession?
        synchronized(lock) {
            if (ended) return
            ended = true
            last = _live.value
            snapshot = snapshot?.copy(stoppedByUser = true)
            stopSpeech(c.elapsed)
        }
        loopJob?.cancel()
        scope.launch {
            fadeOutAndStop()
            persistence.clear()
            effects.onEnded(last, stoppedByUser = true)
            volumeJob?.cancel()
        }
    }

    /** Połączenie telefoniczne (lub inna komunikacja głosowa) rozpoczęte/zakończone. */
    fun onCallStateChanged(inCall: Boolean) = mutate(sync = true) { s, c ->
        if (inCall) {
            if (s.callActive) return@mutate s
            stopSpeech(c.elapsed)
            music?.pause()
            notice = "Połączenie telefoniczne – komunikaty wstrzymane."
            SessionEngine.callStarted(s, c)
        } else {
            if (!s.callActive) return@mutate s
            var r = SessionEngine.callEnded(s, c)
            notice = when {
                r.paused -> "Rutyna wstrzymana po połączeniu. Naciśnij „Wznów”."
                else -> null
            }
            if (!r.paused) {
                music?.play()
                // Po rozmowie przypomnij bieżące zadanie.
                r = withReannouncement(r, c)
            }
            r
        }
    }

    private fun withReannouncement(s: SessionSnapshot, c: ClockReading): SessionSnapshot {
        val st = SessionEngine.evaluate(s, c.elapsed)
        val idx = st.taskIndex ?: return s
        if (st.phase != Phase.ACTIVE && st.phase != Phase.AWAITING_CONFIRMATION) return s
        reannounceKeys += CueKeys.taskStart(idx)
        return s.copy(firedCues = s.firedCues - CueKeys.taskStart(idx))
    }

    private inline fun mutate(sync: Boolean, crossinline f: (SessionSnapshot, ClockReading) -> SessionSnapshot) {
        synchronized(lock) {
            val s = snapshot ?: return
            if (ended) return
            val c = clock.now()
            val updated = f(s, c)
            snapshot = updated
            persist(updated, sync)
            publish(updated, c)
        }
        wake()
    }

    // ---------------------------------------------------------------- pętla

    @Volatile
    private var wakeSignal: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun wake() {
        wakeSignal?.complete(Unit)
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val sleepMs = synchronized(lock) { step() } ?: break
            if (sleepMs > 0) {
                val signal = kotlinx.coroutines.CompletableDeferred<Unit>()
                wakeSignal = signal
                kotlinx.coroutines.withTimeoutOrNull(sleepMs) { signal.await() }
                wakeSignal = null
            }
        }
    }

    /** Jeden krok pętli. Zwraca czas uśpienia albo null, gdy sesja się zakończyła. */
    private fun step(): Long? {
        var s = snapshot ?: return null
        if (ended) return null
        val c = clock.now()
        val status = SessionEngine.evaluate(s, c.elapsed)
        s = SessionEngine.commitDerived(s, status)
        s = SessionEngine.heartbeat(s, c)

        speechWatchdog(c.elapsed)

        // --- Komunikaty (z kompensacją opóźnienia syntezy) ---
        val latency = if (config.latencyCompensation) latencyMs else 0L
        val speakStatus = if (status.paused) status else SessionEngine.evaluateAt(s, status.positionMs + latency)
        if (!status.paused && !s.callActive) {
            val availability = speech.availability
            val due = scheduler.due(s.plan, speakStatus, s.firedCues)
            val utterance = due.utterance
            when {
                utterance == null -> {
                    if (due.handledKeys.isNotEmpty()) s = s.copy(firedCues = s.firedCues + due.handledKeys)
                    reannounceKeys.removeAll(due.handledKeys)
                }
                !s.plan.announcements.enabled ||
                    availability == SpeechAvailability.UNAVAILABLE ||
                    availability == SpeechAvailability.DISABLED -> {
                    // Mowa niedostępna/wyłączona – harmonogram działa dalej bez komunikatów.
                    s = s.copy(firedCues = s.firedCues + due.handledKeys)
                    releaseDuckIfIdle(c.elapsed)
                }
                availability == SpeechAvailability.INITIALIZING -> {
                    // Silnik mowy jeszcze startuje: pomiń tylko nieaktualne, resztę spróbuj za chwilę.
                    val skippedKeys = due.skipped.map { it.key }
                    if (skippedKeys.isNotEmpty()) s = s.copy(firedCues = s.firedCues + skippedKeys)
                }
                utterance.priority == CuePriority.LOW && currentUtteranceId != null -> {
                    // Coś jest właśnie mówione – ostrzeżenie pomijamy, nigdy nie nakładamy mowy.
                    s = s.copy(firedCues = s.firedCues + due.handledKeys)
                }
                else -> {
                    s = s.copy(firedCues = s.firedCues + due.handledKeys)
                    val reannounce = utterance.keys.any { it in reannounceKeys }
                    reannounceKeys.removeAll(due.handledKeys)
                    val text = if (reannounce && !utterance.text.startsWith("Teraz:")) "Teraz: ${utterance.text}" else utterance.text
                    speakNow(utterance.copy(text = text), c, status.positionMs)
                }
            }
            if (preDuckedKey != null && preDuckedKey in s.firedCues && currentUtteranceId == null) {
                // Komunikat, pod który przyciszyliśmy, został pominięty.
                preDuckedKey = null
                releaseDuckIfIdle(c.elapsed)
            }
            maybePreDuck(s, speakStatus, c, latency)
        }

        // --- Zmiana zadania ---
        val key = status.phase to status.taskIndex
        snapshot = s
        val live = publish(s, c, status)
        if (key != lastTaskKey) {
            lastTaskKey = key
            effects.onTaskChanged(live)
        }

        // --- Trwały zapis ---
        val structural = persistedCopy?.let { stripVolatile(it) != stripVolatile(s) } ?: true
        if (structural || c.elapsed - lastPersistElapsed >= config.heartbeatMs) {
            persist(s, sync = structural)
        }

        // --- Zakończenie ---
        if (status.phase == Phase.FINISHED) {
            if (!s.finished) {
                s = s.copy(finished = true)
                snapshot = s
                persist(s, sync = true)
            }
            val finishedAt = finishedAtElapsed ?: c.elapsed.also { finishedAtElapsed = it }
            val endPending = s.plan.announcements.enabled && CueKeys.SESSION_END !in s.firedCues &&
                s.plan.announcements.endMessage.isNotBlank() &&
                speech.availability == SpeechAvailability.INITIALIZING
            val speaking = currentUtteranceId != null
            if ((!speaking && !endPending) || c.elapsed - finishedAt > config.endSpeechTimeoutMs) {
                ended = true
                scope.launch {
                    fadeOutAndStop()
                    persistence.clear()
                    effects.onEnded(_live.value, stoppedByUser = false)
                    volumeJob?.cancel()
                }
                return null
            }
            return config.tickMs
        }

        // --- Uśpienie do najbliższego zdarzenia ---
        if (status.paused) return config.tickMs * 2
        var untilNext = config.tickMs
        scheduler.nextCue(s.plan, speakStatus, s.firedCues)?.let { cue ->
            val untilCue = cue.timeMs - speakStatus.positionMs
            val untilPreDuck = untilCue - ducking.attackMs
            val target = if (untilPreDuck > 0 && shouldDuckFor()) untilPreDuck else untilCue
            untilNext = minOf(untilNext, target.coerceAtLeast(1))
        }
        nextBoundary(status)?.let { untilNext = minOf(untilNext, (it - status.positionMs).coerceAtLeast(1)) }
        return untilNext
    }

    private fun stripVolatile(s: SessionSnapshot) = s.copy(lastAliveElapsed = 0, lastAliveWall = 0)

    private fun nextBoundary(status: SessionStatus): Long? {
        val pos = status.positionMs
        val timeoutEnd = if (status.phase == Phase.AWAITING_CONFIRMATION) {
            status.currentEndMs?.let { it + (snapshot?.plan?.effectiveTimeoutMs ?: 0L) }
        } else {
            null
        }
        return (status.effectiveStartsMs + status.effectiveEndsMs + status.effectiveTotalMs + listOfNotNull(timeoutEnd))
            .filter { it > pos }
            .minOrNull()
    }

    private fun shouldDuckFor(): Boolean =
        config.duckingEnabled && music != null &&
            snapshot?.plan?.musicPlayback == MusicPlayback.EMBEDDED

    private fun maybePreDuck(s: SessionSnapshot, speakStatus: SessionStatus, c: ClockReading, latency: Long) {
        if (!shouldDuckFor() || !s.plan.announcements.enabled) return
        if (speech.availability != SpeechAvailability.READY) return
        val cue = scheduler.nextCue(s.plan, speakStatus, s.firedCues) ?: return
        if (cue.priority == CuePriority.LOW && currentUtteranceId != null) return
        val untilCue = cue.timeMs - speakStatus.positionMs
        if (untilCue <= ducking.attackMs && preDuckedKey != cue.key) {
            preDuckedKey = cue.key
            holdDuck(c.elapsed)
        }
    }

    private fun holdDuck(now: Long) {
        if (!shouldDuckFor()) return
        if (!duckHeld) {
            duckHeld = true
            duckHeldSince = now
            ducking.duck(now)
        }
    }

    private fun releaseDuckIfIdle(now: Long) {
        if (duckHeld && currentUtteranceId == null) {
            duckHeld = false
            ducking.release(now)
        }
    }

    private fun speakNow(u: Utterance, c: ClockReading, positionMs: Long) {
        val id = "u${++utteranceCounter}"
        holdDuck(c.elapsed)
        if (currentUtteranceId == null) effects.onSpeechAudioFocus(true)
        currentUtteranceId = id
        currentUtteranceStartedAt = c.elapsed
        currentUtteranceDeadline = c.elapsed + estimator.estimateMs(u.text) + config.speechWatchdogExtraMs
        speakCalledAt[id] = c.elapsed
        preDuckedKey = null
        spokenLog += SpokenRecord(c.elapsed, positionMs, u.text, u.keys)
        speech.speak(id, u.text, flush = true)
    }

    private fun stopSpeech(now: Long) {
        if (currentUtteranceId != null) {
            currentUtteranceId = null
            speech.stop()
            effects.onSpeechAudioFocus(false)
        }
        preDuckedKey = null
        releaseDuckIfIdle(now)
    }

    private fun speechWatchdog(now: Long) {
        if (currentUtteranceId != null && now > currentUtteranceDeadline) {
            currentUtteranceId = null
            effects.onSpeechAudioFocus(false)
            releaseDuckIfIdle(now)
        }
        if (duckHeld && currentUtteranceId == null && preDuckedKey == null && now - duckHeldSince > 15_000) {
            releaseDuckIfIdle(now)
        }
    }

    // ---------------------------------------------------------------- SpeechListener

    override fun onStart(id: String) {
        synchronized(lock) {
            val called = speakCalledAt.remove(id) ?: return
            val sample = (clock.now().elapsed - called).coerceIn(0, config.maxLatencyMs)
            latencyMs = ((latencyMs * 7 + sample * 3) / 10).coerceIn(0, config.maxLatencyMs)
        }
    }

    override fun onDone(id: String) = finishUtterance(id)

    override fun onError(id: String) = finishUtterance(id)

    private fun finishUtterance(id: String) {
        synchronized(lock) {
            speakCalledAt.remove(id)
            if (currentUtteranceId != id) return
            currentUtteranceId = null
            val now = clock.now().elapsed
            effects.onSpeechAudioFocus(false)
            if (preDuckedKey == null) releaseDuckIfIdle(now)
        }
        wake()
    }

    // ---------------------------------------------------------------- głośność muzyki

    private var lastSentVolume = -1f

    private suspend fun volumeLoop() {
        while (scope.isActive) {
            val now = clock.now().elapsed
            val plan = currentSnapshot?.plan
            if (plan != null && music != null && plan.musicPlayback == MusicPlayback.EMBEDDED) {
                val fade = fadeMultiplier(now)
                val target = plan.musicVolume * ducking.level(now) * fade
                // Podczas przejścia wysyłamy co najmniej 0,25% zmiany; po jego zakończeniu – dokładną wartość.
                if (lastSentVolume < 0 || abs(target - lastSentVolume) >= 0.25f ||
                    (target != lastSentVolume && ducking.isSettled(now) && fadeStartElapsed == null)
                ) {
                    lastSentVolume = target
                    music.setVolume(target)
                }
            }
            val busy = !ducking.isIdle(now) || fadeStartElapsed != null
            delay(if (busy) config.volumeStepMs else config.tickMs)
        }
    }

    private fun fadeMultiplier(now: Long): Float {
        val start = fadeStartElapsed ?: return 1f
        val t = ((now - start).toFloat() / config.fadeOutMs).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    private suspend fun fadeOutAndStop() {
        val m = music
        val plan = currentSnapshot?.plan
        if (m != null && plan != null && plan.musicPlayback == MusicPlayback.EMBEDDED &&
            m.state.value.status == MusicStatus.PLAYING
        ) {
            fadeStartElapsed = clock.now().elapsed
            delay(config.fadeOutMs + 100)
        }
        m?.stop()
    }

    // ---------------------------------------------------------------- publikacja

    private fun publish(s: SessionSnapshot, c: ClockReading, status: SessionStatus = SessionEngine.evaluate(s, c.elapsed)): LiveSession {
        val live = LiveSession(
            sessionId = s.id,
            plan = s.plan,
            status = status,
            music = music?.state?.value ?: MusicState(),
            speech = if (!s.plan.announcements.enabled) SpeechAvailability.DISABLED else speech.availability,
            callActive = s.callActive,
            notice = notice,
            ending = status.phase == Phase.FINISHED,
            nowWall = c.wall,
            nowElapsed = c.elapsed,
        )
        _live.value = live
        effects.onLiveUpdate(live)
        return live
    }

    private fun persist(s: SessionSnapshot, sync: Boolean) {
        persistence.save(s, sync)
        persistedCopy = s
        lastPersistElapsed = s.lastAliveElapsed
    }
}
