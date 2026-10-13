package pl.rytm.app.domain.session

import pl.rytm.app.domain.schedule.ScheduleValidator
import pl.rytm.app.domain.speech.AnnouncementTexts
import pl.rytm.app.domain.speech.SpeechDurationEstimator

enum class CueType { SESSION_START, TASK_START, WARNING, AWAITING_CONFIRMATION, SESSION_END }

enum class CuePriority {
    /** Polecenie zadania / zakończenie – przerywa bieżącą wypowiedź (nigdy się nie nakładają). */
    HIGH,

    /** Ostrzeżenia – pomijane, jeśli coś jest właśnie mówione. */
    LOW,
}

/** Komunikat zaplanowany na konkretną pozycję osi czasu. */
data class Cue(
    val key: String,
    val type: CueType,
    val timeMs: Long,
    val text: String,
    val priority: CuePriority,
    val taskIndex: Int?,
)

/** Wynik obsługi zaległych komunikatów w danym kroku pętli. */
data class DueResult(
    /** Klucze do oznaczenia jako obsłużone (wypowiedziane lub pominięte). */
    val handledKeys: Set<String>,
    /** Jedna wypowiedź (zaległe komunikaty z tej samej chwili są łączone – nigdy się nie nakładają). */
    val utterance: Utterance?,
    val skipped: List<Cue>,
)

data class Utterance(val text: String, val priority: CuePriority, val keys: List<String>, val late: Boolean)

/**
 * Planuje komunikaty na podstawie bieżącego (efektywnego) harmonogramu.
 * Wszystkie czasy są pozycjami osi czasu sesji – nie zależą od wywołań pętli.
 */
class AnnouncementScheduler(private val estimator: SpeechDurationEstimator) {

    fun cues(plan: SessionPlan, status: SessionStatus): List<Cue> {
        val cfg = plan.announcements
        val entries = plan.entries
        val starts = status.effectiveStartsMs
        val ends = status.effectiveEndsMs
        val margin = ScheduleValidator.SPEECH_MARGIN_MS
        val result = mutableListOf<Cue>()

        var previousSpeechEnd = 0L
        if (cfg.startMessage.isNotBlank()) {
            result += Cue(CueKeys.SESSION_START, CueType.SESSION_START, 0L, cfg.startMessage.trim(), CuePriority.HIGH, null)
            previousSpeechEnd = estimator.estimateMs(cfg.startMessage)
        }

        val startCueTimes = LongArray(entries.size)
        val startSpeechMs = LongArray(entries.size)
        for (i in entries.indices) {
            val text = AnnouncementTexts.taskStart(entries[i].spokenText, plan.taskDurationsSec.getOrElse(i) { 0 }, cfg)
            val speechMs = estimator.estimateMs(text)
            val lead = when (plan.announceTiming) {
                AnnounceTiming.AT_TASK_START -> 0L
                AnnounceTiming.FINISH_AT_TASK_START -> speechMs
            }
            // Wyprzedzenie nie może wejść na poprzedni komunikat.
            val earliest = if (i == 0 && cfg.startMessage.isBlank()) 0L else previousSpeechEnd + margin
            val cueTime = maxOf(starts[i] - lead, minOf(earliest, starts[i]), 0L)
            startCueTimes[i] = cueTime
            startSpeechMs[i] = speechMs
            result += Cue(CueKeys.taskStart(i), CueType.TASK_START, cueTime, text, CuePriority.HIGH, i)
            previousSpeechEnd = cueTime + speechMs
        }

        for (i in entries.indices) {
            val nextCue = if (i + 1 < entries.size) startCueTimes[i + 1] else ends[i]
            val warn = cfg.warningBeforeEndSec
            if (warn > 0 && ends[i] - starts[i] >= 2 * warn * 1000L) {
                val warnText = AnnouncementTexts.warning(warn)
                val warnTime = ends[i] - warn * 1000L
                val fits = warnTime >= startCueTimes[i] + startSpeechMs[i] + margin &&
                    warnTime + estimator.estimateMs(warnText) + margin <= nextCue
                if (fits) {
                    result += Cue(CueKeys.warning(i), CueType.WARNING, warnTime, warnText, CuePriority.LOW, i)
                }
            }
            if (plan.effectiveTimeoutMs > 0) {
                result += Cue(
                    CueKeys.awaiting(i), CueType.AWAITING_CONFIRMATION, ends[i],
                    AnnouncementTexts.AWAITING_CONFIRMATION, CuePriority.LOW, i,
                )
            }
        }

        if (cfg.endMessage.isNotBlank()) {
            result += Cue(CueKeys.SESSION_END, CueType.SESSION_END, status.effectiveTotalMs, cfg.endMessage.trim(), CuePriority.HIGH, null)
        }
        return result.sortedBy { it.timeMs }
    }

    /**
     * Zwraca komunikaty, których czas już nadszedł. Komunikaty nieaktualne (np. po długiej przerwie
     * procesu albo pauzie) są pomijane – mówimy tylko o zadaniu, które trwa TERAZ.
     */
    fun due(plan: SessionPlan, status: SessionStatus, fired: Set<String>): DueResult {
        if (status.paused) return DueResult(emptySet(), null, emptyList())
        val pos = status.positionMs
        val pending = cues(plan, status).filter { it.key !in fired && it.timeMs <= pos }
        if (pending.isEmpty()) return DueResult(emptySet(), null, emptyList())

        val speak = mutableListOf<Cue>()
        val skipped = mutableListOf<Cue>()
        val current = status.taskIndex
        val phase = status.phase
        val allCues = cues(plan, status)
        val firstTaskCue = allCues.firstOrNull { it.type == CueType.TASK_START }?.timeMs ?: Long.MAX_VALUE

        for (cue in pending) {
            val lateBy = pos - cue.timeMs
            val i = cue.taskIndex
            val decision: Decision = when (cue.type) {
                CueType.SESSION_START ->
                    if (lateBy <= STALE_START_MS && pos < firstTaskCue) Decision.SPEAK else Decision.SKIP
                CueType.TASK_START -> when {
                    current == null -> Decision.SKIP
                    i!! < current -> Decision.SKIP
                    // Bieżące zadanie (lub zapowiadane z wyprzedzeniem w czasie wstępu/przerwy).
                    i == current -> Decision.SPEAK
                    // Zapowiedź kolejnego zadania z wyprzedzeniem, gdy bieżące jeszcze trwa.
                    phase == Phase.ACTIVE && i == status.nextTaskIndex && pos < status.effectiveStartsMs[i] -> Decision.SPEAK
                    else -> Decision.WAIT
                }
                CueType.WARNING -> when {
                    current == null || i!! < current -> Decision.SKIP
                    i > current -> Decision.WAIT
                    phase == Phase.ACTIVE -> if (lateBy <= STALE_LOW_MS) Decision.SPEAK else Decision.SKIP
                    phase == Phase.AWAITING_CONFIRMATION -> Decision.SKIP
                    else -> Decision.WAIT
                }
                CueType.AWAITING_CONFIRMATION -> when {
                    current == null || i!! < current -> Decision.SKIP
                    i > current -> Decision.WAIT
                    phase == Phase.AWAITING_CONFIRMATION -> if (lateBy <= STALE_LOW_MS) Decision.SPEAK else Decision.SKIP
                    else -> Decision.WAIT
                }
                CueType.SESSION_END -> when {
                    phase != Phase.FINISHED -> Decision.WAIT
                    lateBy <= STALE_END_MS -> Decision.SPEAK
                    else -> Decision.SKIP
                }
            }
            when (decision) {
                Decision.SPEAK -> speak += cue
                Decision.SKIP -> skipped += cue
                Decision.WAIT -> Unit
            }
        }

        // Z kilku zaległych zadań mów tylko o najnowszym (np. po wybudzeniu procesu).
        val taskStarts = speak.filter { it.type == CueType.TASK_START }
        if (taskStarts.size > 1) {
            val keep = taskStarts.maxBy { it.timeMs }
            speak.removeAll { it.type == CueType.TASK_START && it !== keep }
            skipped += taskStarts.filter { it !== keep }
        }
        // Ostrzeżenia nie dokładamy do polecenia zadania.
        if (speak.any { it.priority == CuePriority.HIGH }) {
            val low = speak.filter { it.priority == CuePriority.LOW }
            speak.removeAll(low)
            skipped += low
        }

        val handled = (speak + skipped).map { it.key }.toSet()
        if (speak.isEmpty()) return DueResult(handled, null, skipped)

        val ordered = speak.sortedBy { it.timeMs }
        val late = ordered.any { it.type == CueType.TASK_START && pos - it.timeMs > LATE_PREFIX_MS }
        val texts = ordered.map { cue ->
            if (cue.type == CueType.TASK_START && pos - cue.timeMs > LATE_PREFIX_MS && !cue.text.startsWith("Teraz:")) {
                "Teraz: ${cue.text}"
            } else {
                cue.text
            }
        }
        val priority = if (ordered.any { it.priority == CuePriority.HIGH }) CuePriority.HIGH else CuePriority.LOW
        return DueResult(handled, Utterance(texts.joinToString(" "), priority, ordered.map { it.key }, late), skipped)
    }

    /** Najbliższy jeszcze nieobsłużony komunikat po bieżącej pozycji (do planowania wybudzeń i przyciszania). */
    fun nextCue(plan: SessionPlan, status: SessionStatus, fired: Set<String>): Cue? =
        cues(plan, status).firstOrNull { it.key !in fired && it.timeMs > status.positionMs }

    private enum class Decision { SPEAK, SKIP, WAIT }

    companion object {
        /** Powyżej tego opóźnienia polecenie zadania dostaje przedrostek „Teraz:”. */
        const val LATE_PREFIX_MS = 2_000L
        const val STALE_LOW_MS = 1_500L
        const val STALE_START_MS = 3_000L
        const val STALE_END_MS = 15_000L
    }
}
