package pl.rytm.app.domain.session

import kotlinx.serialization.Serializable
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.schedule.TimelineEntry

/** Kiedy wypowiadać polecenie względem początku zadania. */
@Serializable
enum class AnnounceTiming {
    /** Komunikat zaczyna się dokładnie w chwili rozpoczęcia zadania. */
    AT_TASK_START,

    /** Komunikat zaczyna się wcześniej tak, by skończył się w chwili rozpoczęcia zadania. */
    FINISH_AT_TASK_START,
}

/**
 * Niezmienny plan sesji – wyliczony w całości PRZED jej rozpoczęciem.
 * Edycja rutyny w trakcie sesji nie zmienia trwającego planu.
 */
@Serializable
data class SessionPlan(
    val routineId: Long,
    val routineName: String,
    val entries: List<TimelineEntry>,
    val taskDurationsSec: List<Int>,
    val mode: ScheduleMode,
    val flexibleTimeoutMs: Long,
    val interruptionBehavior: InterruptionBehavior,
    val announcements: AnnouncementConfig,
    val announceTiming: AnnounceTiming = AnnounceTiming.AT_TASK_START,
    val music: MusicSelection? = null,
    val musicPlayback: MusicPlayback = MusicPlayback.NONE,
    val musicVolume: Int = 100,
    val musicLoop: Boolean = true,
    val leaveTimeMinutes: Int? = null,
) {
    val plannedTotalMs: Long get() = entries.lastOrNull()?.endMs ?: 0L

    /** W trybie ścisłym brak okna oczekiwania – to dokładnie ten sam algorytm z limitem 0. */
    val effectiveTimeoutMs: Long get() = if (mode == ScheduleMode.FLEXIBLE) flexibleTimeoutMs.coerceAtLeast(0) else 0L

    companion object {
        fun from(
            routine: Routine,
            tasks: List<RoutineTask> = routine.tasks,
            announceTiming: AnnounceTiming = AnnounceTiming.AT_TASK_START,
            ttsGloballyEnabled: Boolean = true,
        ): SessionPlan = SessionPlan(
            routineId = routine.id,
            routineName = routine.name,
            entries = TimelineBuilder.build(tasks),
            taskDurationsSec = tasks.map { it.durationSec },
            mode = routine.mode,
            flexibleTimeoutMs = routine.flexibleTimeoutSec.coerceAtLeast(0) * 1000L,
            interruptionBehavior = routine.interruptionBehavior,
            announcements = routine.announcements.copy(
                enabled = routine.announcements.enabled && ttsGloballyEnabled,
            ),
            announceTiming = announceTiming,
            music = routine.music,
            musicPlayback = routine.effectiveMusicPlayback,
            musicVolume = routine.musicVolume.coerceIn(0, 100),
            musicLoop = routine.musicLoop,
            leaveTimeMinutes = routine.leaveTimeMinutes,
        )
    }
}

@Serializable
enum class PauseReason { USER, CALL, INTERRUPTED }

/**
 * Trwały stan sesji (zapisywany na dysku). Pozycja na osi czasu jest wyliczana z kotwicy:
 *
 *     pozycja(teraz) = anchorPosition + (teraz − anchorElapsed)   gdy sesja trwa
 *     pozycja(teraz) = anchorPosition                              gdy wstrzymana
 *
 * gdzie „teraz” to zegar monotoniczny (SystemClock.elapsedRealtime – liczy także czas uśpienia
 * urządzenia). Dzięki temu stan nie zależy od tego, ile razy wywołano pętlę odliczania
 * ani od tego, czy ekran był włączony.
 */
@Serializable
data class SessionSnapshot(
    val id: String,
    val plan: SessionPlan,
    val bootCount: Int,
    val startedWall: Long,
    val anchorElapsed: Long,
    val anchorWall: Long,
    val anchorPosition: Long,
    val paused: Boolean = false,
    val pauseReason: PauseReason? = null,
    /** Tryb elastyczny: przesunięcie (ms) po zakończeniu kolejnych zadań: rzeczywisty koniec − planowany koniec. */
    val completedShifts: List<Long> = emptyList(),
    val lastAliveElapsed: Long,
    val lastAliveWall: Long,
    /** Indeks zadania trwającego w chwili rozpoczęcia przerwy (połączenie). */
    val interruptedTaskIndex: Int? = null,
    val callActive: Boolean = false,
    /** Klucze komunikatów już obsłużonych (wypowiedzianych lub świadomie pominiętych). */
    val firedCues: Set<String> = emptySet(),
    val finished: Boolean = false,
    val stoppedByUser: Boolean = false,
)

enum class Phase {
    /** Przed pierwszym zadaniem (np. wstęp muzyczny 00:00–00:10). */
    INTRO,

    /** Zadanie trwa. */
    ACTIVE,

    /** Tryb elastyczny: czas zadania minął, czekamy na potwierdzenie (ograniczony limit). */
    AWAITING_CONFIRMATION,

    /** Przerwa między zadaniami. */
    GAP,

    /** Wszystkie zadania zakończone. */
    FINISHED,
}

/** Wyliczony stan sesji w danej chwili. Czysta funkcja migawki i zegara. */
data class SessionStatus(
    val phase: Phase,
    val positionMs: Long,
    val paused: Boolean,
    val pauseReason: PauseReason?,
    /** Zadanie bieżące (ACTIVE / AWAITING_CONFIRMATION) albo najbliższe (INTRO / GAP). */
    val taskIndex: Int?,
    val nextTaskIndex: Int?,
    /** Efektywne czasy startu wszystkich zadań (w trybie elastycznym uwzględniają przesunięcia). */
    val effectiveStartsMs: List<Long>,
    /** Efektywne planowane końce wszystkich zadań. */
    val effectiveEndsMs: List<Long>,
    val shifts: List<Long>,
    val effectiveTotalMs: Long,
    val plannedTotalMs: Long,
) {
    val currentStartMs: Long? get() = taskIndex?.let { effectiveStartsMs[it] }
    val currentEndMs: Long? get() = taskIndex?.let { effectiveEndsMs[it] }

    /** Pozostały czas bieżącego zadania (ACTIVE) albo do jego startu (INTRO/GAP). */
    val remainingInPhaseMs: Long
        get() = when (phase) {
            Phase.ACTIVE -> (currentEndMs!! - positionMs).coerceAtLeast(0)
            Phase.INTRO, Phase.GAP -> (currentStartMs!! - positionMs).coerceAtLeast(0)
            Phase.AWAITING_CONFIRMATION -> 0
            Phase.FINISHED -> 0
        }

    val totalRemainingMs: Long get() = (effectiveTotalMs - positionMs).coerceAtLeast(0)

    val taskProgress: Float
        get() {
            val idx = taskIndex ?: return if (phase == Phase.FINISHED) 1f else 0f
            if (phase != Phase.ACTIVE && phase != Phase.AWAITING_CONFIRMATION) return 0f
            val dur = effectiveEndsMs[idx] - effectiveStartsMs[idx]
            if (dur <= 0) return 1f
            return ((positionMs - effectiveStartsMs[idx]).toFloat() / dur).coerceIn(0f, 1f)
        }

    val totalProgress: Float
        get() = if (effectiveTotalMs <= 0) 1f else (positionMs.toFloat() / effectiveTotalMs).coerceIn(0f, 1f)
}
