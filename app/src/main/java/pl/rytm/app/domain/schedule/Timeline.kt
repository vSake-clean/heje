package pl.rytm.app.domain.schedule

import kotlinx.serialization.Serializable
import pl.rytm.app.domain.model.RoutineTask

/**
 * Jedno zadanie na osi czasu, z bezwzględnymi czasami względem początku sesji (ms).
 * Wszystkie momenty są wyliczane przed rozpoczęciem sesji.
 */
@Serializable
data class TimelineEntry(
    val index: Int,
    val taskId: Long,
    val title: String,
    val spokenText: String,
    val startMs: Long,
    val endMs: Long,
    val mandatory: Boolean = true,
    val notes: String = "",
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Model osi czasu: zadania następują po sobie, a każde może mieć przerwę przed sobą.
 * Dzięki temu zadania nie mogą na siebie nachodzić – przesunięcie zadania przesuwa też kolejne.
 */
object TimelineBuilder {

    fun build(tasks: List<RoutineTask>): List<TimelineEntry> {
        var cursorMs = 0L
        return tasks.mapIndexed { index, task ->
            val start = cursorMs + task.gapBeforeSec.coerceAtLeast(0) * 1000L
            val end = start + task.durationSec.coerceAtLeast(0) * 1000L
            cursorMs = end
            TimelineEntry(
                index = index,
                taskId = task.id,
                title = task.title,
                spokenText = task.announcementText,
                startMs = start,
                endMs = end,
                mandatory = task.mandatory,
                notes = task.notes,
            )
        }
    }

    fun totalSec(tasks: List<RoutineTask>): Int =
        tasks.sumOf { it.gapBeforeSec.coerceAtLeast(0) + it.durationSec.coerceAtLeast(0) }

    fun startSecOf(tasks: List<RoutineTask>, index: Int): Int {
        var cursor = 0
        for (i in 0..index) {
            cursor += tasks[i].gapBeforeSec.coerceAtLeast(0)
            if (i < index) cursor += tasks[i].durationSec.coerceAtLeast(0)
        }
        return cursor
    }

    fun endSecOf(tasks: List<RoutineTask>, index: Int): Int =
        startSecOf(tasks, index) + tasks[index].durationSec.coerceAtLeast(0)
}

/** Operacje edycji harmonogramu. Zwracają nową listę – nie modyfikują wejścia. */
object ScheduleEditing {

    sealed interface StartChange {
        data class Ok(val tasks: List<RoutineTask>) : StartChange

        /** Nowy start nachodziłby na poprzednie zadanie, które kończy się o [previousEndSec]. */
        data class Overlap(val previousEndSec: Int) : StartChange
    }

    /** Ustawia bezwzględny czas rozpoczęcia zadania (kolejne zadania przesuwają się razem z nim). */
    fun setStart(tasks: List<RoutineTask>, index: Int, newStartSec: Int): StartChange {
        val previousEnd = if (index == 0) 0 else TimelineBuilder.endSecOf(tasks, index - 1)
        if (newStartSec < previousEnd) return StartChange.Overlap(previousEnd)
        return StartChange.Ok(
            tasks.replaceAt(index) { it.copy(gapBeforeSec = newStartSec - previousEnd) },
        )
    }

    /** Przesuwa zadanie (i wszystkie kolejne) o [deltaSec]; nie pozwala wejść na poprzednie zadanie. */
    fun shift(tasks: List<RoutineTask>, index: Int, deltaSec: Int): List<RoutineTask> =
        tasks.replaceAt(index) { it.copy(gapBeforeSec = (it.gapBeforeSec + deltaSec).coerceAtLeast(0)) }

    fun setDuration(tasks: List<RoutineTask>, index: Int, durationSec: Int): List<RoutineTask> =
        tasks.replaceAt(index) { it.copy(durationSec = durationSec.coerceAtLeast(1)) }

    fun move(tasks: List<RoutineTask>, from: Int, to: Int): List<RoutineTask> {
        if (from !in tasks.indices || to !in tasks.indices || from == to) return tasks
        val list = tasks.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list
    }

    fun duplicate(tasks: List<RoutineTask>, index: Int): List<RoutineTask> {
        val list = tasks.toMutableList()
        list.add(index + 1, tasks[index].copy(id = 0, gapBeforeSec = 0))
        return list
    }

    fun remove(tasks: List<RoutineTask>, index: Int): List<RoutineTask> =
        tasks.filterIndexed { i, _ -> i != index }

    fun insert(tasks: List<RoutineTask>, index: Int, task: RoutineTask): List<RoutineTask> {
        val list = tasks.toMutableList()
        list.add(index.coerceIn(0, list.size), task)
        return list
    }

    /** Usuwa wszystkie przerwy – zadania następują bezpośrednio po sobie. */
    fun removeGaps(tasks: List<RoutineTask>): List<RoutineTask> = tasks.map { it.copy(gapBeforeSec = 0) }

    private inline fun List<RoutineTask>.replaceAt(index: Int, f: (RoutineTask) -> RoutineTask) =
        mapIndexed { i, t -> if (i == index) f(t) else t }
}
