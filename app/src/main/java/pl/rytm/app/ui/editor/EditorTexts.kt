package pl.rytm.app.ui.editor

import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.schedule.RoutinePlanner
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.text.TimeText
import java.util.Locale

fun seconds1(ms: Long): String = String.format(Locale("pl", "PL"), "%.1f s", ms / 1000.0)

fun describeIssue(issue: ScheduleIssue, tasks: List<RoutineTask>): String {
    fun name(i: Int?) = i?.let { tasks.getOrNull(it)?.title?.ifBlank { "zadanie ${it + 1}" } ?: "zadanie ${it + 1}" } ?: ""
    return when (issue) {
        ScheduleIssue.NoTasks -> "Dodaj przynajmniej jedno zadanie."
        is ScheduleIssue.ZeroDuration -> "„${name(issue.taskIndex)}” ma zerowy czas trwania."
        is ScheduleIssue.EmptyTitle -> "Zadanie ${issue.taskIndex + 1} nie ma nazwy."
        is ScheduleIssue.ExceedsWindow ->
            "Zadania trwają ${TimeText.clockSec(issue.totalSec.toLong())}, a dostępne jest " +
                "${TimeText.clockSec(issue.availableSec.toLong())} – brakuje ${TimeText.short(issue.overBySec)}."
        is ScheduleIssue.SpeechTooLong ->
            "Komunikat „${name(issue.taskIndex)}” trwa ok. ${seconds1(issue.estimatedMs)}, a do kolejnego jest tylko " +
                "${seconds1(issue.availableMs)} – zostanie przerwany. Skróć tekst albo wydłuż zadanie."
        is ScheduleIssue.WarningSkipped ->
            "„${name(issue.taskIndex)}”: ostrzeżenie przed końcem nie zmieści się i zostanie pominięte."
        is ScheduleIssue.Slack -> "Zapas czasu: ${TimeText.short(issue.slackSec)}."
    }
}

fun describeChange(change: RoutinePlanner.Change): String = when (change) {
    is RoutinePlanner.Change.GapReduced ->
        "Przerwa przed „${change.title}”: ${TimeText.short(change.fromSec)} → ${TimeText.short(change.toSec)}"
    is RoutinePlanner.Change.Shortened ->
        "Skrócono „${change.title}”: ${TimeText.short(change.fromSec)} → ${TimeText.short(change.toSec)}"
    is RoutinePlanner.Change.Removed -> "Usunięto „${change.title}” (${TimeText.short(change.durationSec)})"
}
