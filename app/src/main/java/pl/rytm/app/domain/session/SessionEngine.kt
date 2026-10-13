package pl.rytm.app.domain.session

import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.ScheduleMode

/** Odczyt zegarów w jednej chwili. `elapsed` – zegar monotoniczny, `wall` – zegar ścienny. */
data class ClockReading(val elapsed: Long, val wall: Long, val bootCount: Int)

/**
 * Silnik harmonogramu: czyste funkcje bez stanu i bez zależności od Androida.
 *
 * Każdy stan jest wyliczany od nowa z migawki i bieżącego odczytu zegara monotonicznego,
 * więc opóźnione lub pominięte wywołania pętli (wygaszony ekran, przeciążony telefon)
 * nie rozregulowują harmonogramu – po prostu następny odczyt pokazuje właściwy stan.
 */
object SessionEngine {

    /** Przerwa w „biciu serca” sesji dłuższa niż ten próg oznacza przerwanie (np. zabicie procesu). */
    const val DEFAULT_INTERRUPTION_THRESHOLD_MS = 5_000L

    fun start(id: String, plan: SessionPlan, clock: ClockReading): SessionSnapshot = SessionSnapshot(
        id = id,
        plan = plan,
        bootCount = clock.bootCount,
        startedWall = clock.wall,
        anchorElapsed = clock.elapsed,
        anchorWall = clock.wall,
        anchorPosition = 0L,
        lastAliveElapsed = clock.elapsed,
        lastAliveWall = clock.wall,
    )

    fun position(s: SessionSnapshot, nowElapsed: Long): Long =
        if (s.paused) s.anchorPosition else s.anchorPosition + (nowElapsed - s.anchorElapsed).coerceAtLeast(0)

    fun evaluate(s: SessionSnapshot, nowElapsed: Long): SessionStatus =
        evaluateAt(s, position(s, nowElapsed))

    /**
     * Stan na pozycji [pos] osi czasu. Tryb ścisły to ten sam algorytm z oknem oczekiwania = 0
     * i bez potwierdzeń – zadanie kończy się dokładnie o planowanej porze.
     */
    fun evaluateAt(s: SessionSnapshot, pos: Long): SessionStatus {
        val plan = s.plan
        val entries = plan.entries
        val n = entries.size
        val timeout = plan.effectiveTimeoutMs
        val shifts: MutableList<Long> =
            if (plan.mode == ScheduleMode.STRICT) mutableListOf() else s.completedShifts.take(n).toMutableList()

        // Wylicz domyślne zakończenia (po upływie limitu oczekiwania) – czysto z pozycji.
        var i = shifts.size
        while (i < n) {
            val prevShift = if (i == 0) 0L else shifts[i - 1]
            val expectedEnd = entries[i].endMs + prevShift
            if (pos >= expectedEnd + timeout) {
                shifts.add(prevShift + timeout)
                i++
            } else {
                break
            }
        }

        // Przesunięcie zakładane dla przyszłych zadań: jeśli bieżące zadanie czeka na potwierdzenie,
        // kolejne zaczną się najwcześniej „teraz” (gdyby potwierdzić w tej chwili).
        val prevShift = shifts.lastOrNull() ?: 0L
        val provisional = if (i < n && pos >= entries[i].endMs + prevShift) pos - entries[i].endMs else prevShift
        fun shiftBefore(j: Int): Long = if (j == 0) 0L else shifts.getOrElse(j - 1) { provisional }
        val starts = List(n) { j -> entries[j].startMs + shiftBefore(j) }
        val ends = List(n) { j -> entries[j].endMs + shiftBefore(j) }
        val effectiveTotal = when {
            n == 0 -> 0L
            i == n -> entries[n - 1].endMs + shifts[n - 1]
            else -> ends[n - 1]
        }

        if (n == 0 || i == n) {
            return SessionStatus(
                phase = Phase.FINISHED, positionMs = pos, paused = s.paused, pauseReason = s.pauseReason,
                taskIndex = null, nextTaskIndex = null, effectiveStartsMs = starts, effectiveEndsMs = ends,
                shifts = shifts, effectiveTotalMs = effectiveTotal, plannedTotalMs = plan.plannedTotalMs,
            )
        }

        val phase = when {
            pos < starts[i] -> if (i == 0) Phase.INTRO else Phase.GAP
            pos < ends[i] -> Phase.ACTIVE
            else -> Phase.AWAITING_CONFIRMATION
        }
        val next = when (phase) {
            Phase.INTRO, Phase.GAP -> i
            else -> (i + 1).takeIf { it < n }
        }
        return SessionStatus(
            phase = phase, positionMs = pos, paused = s.paused, pauseReason = s.pauseReason,
            taskIndex = i, nextTaskIndex = next, effectiveStartsMs = starts, effectiveEndsMs = ends,
            shifts = shifts, effectiveTotalMs = effectiveTotal, plannedTotalMs = plan.plannedTotalMs,
        )
    }

    fun heartbeat(s: SessionSnapshot, clock: ClockReading): SessionSnapshot =
        s.copy(lastAliveElapsed = clock.elapsed, lastAliveWall = clock.wall)

    fun pause(s: SessionSnapshot, clock: ClockReading, reason: PauseReason): SessionSnapshot {
        if (s.paused) return s
        return s.copy(
            anchorPosition = position(s, clock.elapsed),
            anchorElapsed = clock.elapsed,
            anchorWall = clock.wall,
            paused = true,
            pauseReason = reason,
        ).let { heartbeat(it, clock) }
    }

    fun resume(s: SessionSnapshot, clock: ClockReading): SessionSnapshot {
        if (!s.paused) return s
        return s.copy(
            anchorElapsed = clock.elapsed,
            anchorWall = clock.wall,
            paused = false,
            pauseReason = null,
        ).let { heartbeat(it, clock) }
    }

    /**
     * Wznawia sesję od początku zadania [index] (zachowanie „wznów od aktualnego zadania”).
     * Kolejne zadania przesuwają się o czas przerwy – to świadoma decyzja użytkownika.
     */
    fun resumeFromTaskStart(s: SessionSnapshot, clock: ClockReading, index: Int): SessionSnapshot {
        val status = evaluateAt(s, position(s, clock.elapsed))
        val keepShifts = status.shifts.take(index)
        val shiftBefore = if (index == 0) 0L else keepShifts.getOrElse(index - 1) { 0L }
        val startPos = s.plan.entries[index].startMs + shiftBefore
        return s.copy(
            anchorPosition = startPos,
            anchorElapsed = clock.elapsed,
            anchorWall = clock.wall,
            paused = false,
            pauseReason = null,
            completedShifts = if (s.plan.mode == ScheduleMode.FLEXIBLE) keepShifts else emptyList(),
            firedCues = s.firedCues.filterNot { CueKeys.indexOf(it)?.let { i -> i >= index } ?: false }.toSet(),
            interruptedTaskIndex = null,
        ).let { heartbeat(it, clock) }
    }

    /**
     * Tryb elastyczny: potwierdzenie wykonania bieżącego zadania. Kolejne zadanie zaczyna się
     * po swojej przerwie liczonej od chwili potwierdzenia. W trybie ścisłym nic nie zmienia.
     */
    fun confirm(s: SessionSnapshot, clock: ClockReading): SessionSnapshot {
        if (s.plan.mode != ScheduleMode.FLEXIBLE || s.paused) return s
        val pos = position(s, clock.elapsed)
        val status = evaluateAt(s, pos)
        val idx = status.taskIndex ?: return s
        if (status.phase != Phase.ACTIVE && status.phase != Phase.AWAITING_CONFIRMATION) return s
        val shift = pos - s.plan.entries[idx].endMs
        return s.copy(completedShifts = status.shifts.take(idx) + shift)
    }

    /** Utrwala wyliczone domyślne zakończenia (tryb elastyczny), żeby migawka była samowystarczalna. */
    fun commitDerived(s: SessionSnapshot, status: SessionStatus): SessionSnapshot =
        if (s.plan.mode == ScheduleMode.FLEXIBLE && status.shifts.size > s.completedShifts.size) {
            s.copy(completedShifts = status.shifts)
        } else {
            s
        }

    fun callStarted(s: SessionSnapshot, clock: ClockReading): SessionSnapshot {
        if (s.callActive || s.finished) return s
        val withFlag = s.copy(callActive = true)
        return when (s.plan.interruptionBehavior) {
            InterruptionBehavior.CONTINUE_SCHEDULE -> withFlag
            InterruptionBehavior.PAUSE ->
                if (s.paused) withFlag else pause(withFlag, clock, PauseReason.CALL)
            InterruptionBehavior.RESUME_CURRENT_TASK -> {
                if (s.paused) {
                    withFlag
                } else {
                    val st = evaluate(s, clock.elapsed)
                    val idx = st.taskIndex.takeIf {
                        st.phase == Phase.ACTIVE || st.phase == Phase.AWAITING_CONFIRMATION
                    }
                    pause(withFlag.copy(interruptedTaskIndex = idx), clock, PauseReason.CALL)
                }
            }
        }
    }

    fun callEnded(s: SessionSnapshot, clock: ClockReading): SessionSnapshot {
        if (!s.callActive) return s
        val cleared = s.copy(callActive = false)
        return when (s.plan.interruptionBehavior) {
            InterruptionBehavior.CONTINUE_SCHEDULE -> cleared
            // Pozostaje wstrzymana – użytkownik wznawia ręcznie.
            InterruptionBehavior.PAUSE -> cleared
            InterruptionBehavior.RESUME_CURRENT_TASK -> {
                if (!s.paused || s.pauseReason != PauseReason.CALL) {
                    cleared
                } else {
                    val idx = s.interruptedTaskIndex
                    if (idx != null) resumeFromTaskStart(cleared, clock, idx) else resume(cleared, clock)
                }
            }
        }
    }

    data class Recovery(val snapshot: SessionSnapshot, val interrupted: Boolean, val gapMs: Long)

    /**
     * Odtwarza sesję po ponownym uruchomieniu procesu. Jeśli od ostatniego „bicia serca” minęło
     * więcej niż [thresholdMs], stosuje zachowanie wybrane przez użytkownika.
     * Po restarcie telefonu (inny licznik uruchomień) korzysta z zegara ściennego.
     */
    fun recover(
        s: SessionSnapshot,
        clock: ClockReading,
        thresholdMs: Long = DEFAULT_INTERRUPTION_THRESHOLD_MS,
    ): Recovery {
        val sameBoot = clock.bootCount == s.bootCount && clock.elapsed >= s.lastAliveElapsed
        val posAtAlive = if (s.paused) s.anchorPosition else s.anchorPosition + (s.lastAliveElapsed - s.anchorElapsed)
        val gapMs = if (sameBoot) clock.elapsed - s.lastAliveElapsed else (clock.wall - s.lastAliveWall).coerceAtLeast(0)

        val reanchored = s.copy(
            bootCount = clock.bootCount,
            anchorElapsed = clock.elapsed,
            anchorWall = clock.wall,
            anchorPosition = if (s.paused) s.anchorPosition else posAtAlive + gapMs,
            lastAliveElapsed = clock.elapsed,
            lastAliveWall = clock.wall,
            callActive = false,
        )
        if (gapMs <= thresholdMs || s.paused) return Recovery(reanchored, interrupted = false, gapMs = gapMs)

        val result = when (s.plan.interruptionBehavior) {
            InterruptionBehavior.CONTINUE_SCHEDULE -> reanchored
            InterruptionBehavior.PAUSE -> reanchored.copy(
                anchorPosition = posAtAlive,
                paused = true,
                pauseReason = PauseReason.INTERRUPTED,
            )
            InterruptionBehavior.RESUME_CURRENT_TASK -> {
                val st = evaluateAt(s, posAtAlive)
                val idx = st.taskIndex.takeIf {
                    st.phase == Phase.ACTIVE || st.phase == Phase.AWAITING_CONFIRMATION
                }
                if (idx == null) {
                    reanchored.copy(anchorPosition = posAtAlive)
                } else {
                    val keep = st.shifts.take(idx)
                    val shiftBefore = if (idx == 0) 0L else keep.getOrElse(idx - 1) { 0L }
                    reanchored.copy(
                        anchorPosition = s.plan.entries[idx].startMs + shiftBefore,
                        completedShifts = if (s.plan.mode == ScheduleMode.FLEXIBLE) keep else emptyList(),
                        firedCues = s.firedCues.filterNot { CueKeys.indexOf(it)?.let { i -> i >= idx } ?: false }.toSet(),
                    )
                }
            }
        }
        return Recovery(result, interrupted = true, gapMs = gapMs)
    }
}

/** Klucze identyfikujące komunikaty (zapisywane w migawce, by nie powtarzać ich po wznowieniu). */
object CueKeys {
    const val SESSION_START = "session-start"
    const val SESSION_END = "session-end"
    fun taskStart(i: Int) = "start:$i"
    fun warning(i: Int) = "warn:$i"
    fun awaiting(i: Int) = "await:$i"

    fun indexOf(key: String): Int? = key.substringAfter(':', "").toIntOrNull()
}
