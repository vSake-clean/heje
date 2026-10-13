package pl.rytm.app.domain.schedule

import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.speech.AnnouncementTexts
import pl.rytm.app.domain.speech.SpeechDurationEstimator

enum class Severity { ERROR, WARNING, INFO }

/** Problem w harmonogramie wykryty podczas tworzenia planu. */
sealed interface ScheduleIssue {
    val severity: Severity
    val taskIndex: Int? get() = null

    data object NoTasks : ScheduleIssue {
        override val severity = Severity.ERROR
    }

    data class ZeroDuration(override val taskIndex: Int) : ScheduleIssue {
        override val severity = Severity.ERROR
    }

    data class EmptyTitle(override val taskIndex: Int) : ScheduleIssue {
        override val severity = Severity.ERROR
    }

    /** Zadania nie mieszczą się w dostępnym czasie (pobudka → wyjście lub docelowy czas sesji). */
    data class ExceedsWindow(val totalSec: Int, val availableSec: Int) : ScheduleIssue {
        override val severity = Severity.ERROR
        val overBySec: Int get() = totalSec - availableSec
    }

    /** Komunikat trwa dłużej niż czas do kolejnego komunikatu – zostałby przerwany. */
    data class SpeechTooLong(
        override val taskIndex: Int,
        val estimatedMs: Long,
        val availableMs: Long,
    ) : ScheduleIssue {
        override val severity = Severity.WARNING
    }

    /** Ostrzeżenie „zostało X s” nie zmieści się – zostanie pominięte. */
    data class WarningSkipped(override val taskIndex: Int) : ScheduleIssue {
        override val severity = Severity.INFO
    }

    /** Wolny czas pozostały do wyjścia. */
    data class Slack(val slackSec: Int) : ScheduleIssue {
        override val severity = Severity.INFO
    }
}

object ScheduleValidator {

    /** Minimalny odstęp między końcem jednego komunikatu a początkiem kolejnego. */
    const val SPEECH_MARGIN_MS = 300L

    fun validate(
        tasks: List<RoutineTask>,
        availableSec: Int?,
        announcements: AnnouncementConfig,
        estimator: SpeechDurationEstimator,
    ): List<ScheduleIssue> {
        if (tasks.isEmpty()) return listOf(ScheduleIssue.NoTasks)
        val issues = mutableListOf<ScheduleIssue>()
        tasks.forEachIndexed { i, t ->
            if (t.title.isBlank()) issues += ScheduleIssue.EmptyTitle(i)
            if (t.durationSec <= 0) issues += ScheduleIssue.ZeroDuration(i)
        }
        val total = TimelineBuilder.totalSec(tasks)
        if (availableSec != null) {
            if (total > availableSec) {
                issues += ScheduleIssue.ExceedsWindow(total, availableSec)
            } else if (availableSec - total > 0) {
                issues += ScheduleIssue.Slack(availableSec - total)
            }
        }
        if (announcements.enabled) {
            val entries = TimelineBuilder.build(tasks)
            entries.forEachIndexed { i, e ->
                val text = AnnouncementTexts.taskStart(e.spokenText, tasks[i].durationSec, announcements)
                val speechMs = estimator.estimateMs(text)
                // Komunikat ma czas do początku kolejnego zadania (albo do końca ostatniego).
                val nextCueMs = entries.getOrNull(i + 1)?.startMs ?: e.endMs
                val availableMs = nextCueMs - e.startMs
                if (speechMs + SPEECH_MARGIN_MS > availableMs && availableMs > 0) {
                    issues += ScheduleIssue.SpeechTooLong(i, speechMs, availableMs)
                }
                val warn = announcements.warningBeforeEndSec
                // Ostrzeżenie ma sens tylko w zadaniach co najmniej dwa razy dłuższych niż ono samo.
                if (warn > 0 && e.durationMs >= 2 * warn * 1000L) {
                    val warnText = AnnouncementTexts.warning(warn)
                    val warnStart = e.endMs - warn * 1000L
                    val fits = warnStart >= e.startMs + speechMs + SPEECH_MARGIN_MS &&
                        warnStart + estimator.estimateMs(warnText) + SPEECH_MARGIN_MS <= nextCueMs
                    if (!fits) issues += ScheduleIssue.WarningSkipped(i)
                }
            }
        }
        return issues
    }

    fun hasErrors(issues: List<ScheduleIssue>) = issues.any { it.severity == Severity.ERROR }
}
