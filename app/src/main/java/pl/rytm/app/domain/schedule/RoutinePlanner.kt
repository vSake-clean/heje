package pl.rytm.app.domain.schedule

import pl.rytm.app.domain.model.RoutineTask
import kotlin.math.floor

/**
 * Dopasowuje rutynę do dostępnego czasu (np. pobudka 6:30 → wyjście 7:00).
 *
 * Kolejność ustępstw – od najmniej do najbardziej odczuwalnych:
 *  1. skrócenie/usunięcie przerw,
 *  2. proporcjonalne skrócenie zadań opcjonalnych (do ich minimalnego czasu),
 *  3. usunięcie zadań opcjonalnych.
 * Zadania obowiązkowe NIGDY nie są zmieniane – jeśli same się nie mieszczą, planer to zgłasza.
 * Wynik jest tylko propozycją: użytkownik musi ją zaakceptować.
 */
object RoutinePlanner {

    sealed interface Change {
        val title: String

        data class GapReduced(override val title: String, val fromSec: Int, val toSec: Int) : Change
        data class Shortened(override val title: String, val fromSec: Int, val toSec: Int) : Change
        data class Removed(override val title: String, val durationSec: Int) : Change
    }

    sealed interface Result {
        /** Plan mieści się w dostępnym czasie. */
        data class Fits(val totalSec: Int, val slackSec: Int) : Result

        /** Propozycja zmian, po których plan się zmieści. */
        data class Proposal(
            val tasks: List<RoutineTask>,
            val changes: List<Change>,
            val originalTotalSec: Int,
            val newTotalSec: Int,
            val availableSec: Int,
        ) : Result

        /** Same zadania obowiązkowe trwają dłużej niż dostępny czas. */
        data class Impossible(
            val mandatorySec: Int,
            val availableSec: Int,
            /** Najlepsza możliwa wersja (bez przerw i zadań opcjonalnych). */
            val bestEffort: List<RoutineTask>,
        ) : Result
    }

    /** Minimalny czas zadania opcjonalnego przy skracaniu, gdy użytkownik go nie podał: połowa, min. 5 s. */
    fun minDurationOf(task: RoutineTask): Int {
        val explicit = task.minDurationSec
        val min = explicit ?: maxOf(5, task.durationSec / 2)
        return min.coerceIn(1, task.durationSec.coerceAtLeast(1))
    }

    fun fit(tasks: List<RoutineTask>, availableSec: Int): Result {
        val total = TimelineBuilder.totalSec(tasks)
        if (total <= availableSec) return Result.Fits(total, availableSec - total)

        val mandatorySec = tasks.filter { it.mandatory }.sumOf { it.durationSec }
        if (mandatorySec > availableSec) {
            return Result.Impossible(
                mandatorySec = mandatorySec,
                availableSec = availableSec,
                bestEffort = tasks.filter { it.mandatory }.map { it.copy(gapBeforeSec = 0) },
            )
        }

        var deficit = total - availableSec
        val changes = mutableListOf<Change>()
        var working = tasks.toMutableList()

        // 1. Przerwy – od końca rutyny.
        for (i in working.indices.reversed()) {
            if (deficit <= 0) break
            val gap = working[i].gapBeforeSec
            if (gap <= 0) continue
            val cut = minOf(gap, deficit)
            working[i] = working[i].copy(gapBeforeSec = gap - cut)
            changes += Change.GapReduced(working[i].title, gap, gap - cut)
            deficit -= cut
        }
        if (deficit <= 0) return proposal(tasks, working, changes, total, availableSec)

        // 3 (przygotowanie). Jeśli skracanie nie wystarczy – usuń zadania opcjonalne (zachłannie:
        // najpierw najmniejsze, które samo pokrywa brak, w przeciwnym razie największe).
        fun compressible(list: List<RoutineTask>) =
            list.filter { !it.mandatory }.sumOf { it.durationSec - minDurationOf(it) }

        while (deficit > compressible(working)) {
            val optional = working.withIndex().filter { !it.value.mandatory }
            if (optional.isEmpty()) break
            val others = { idx: Int -> compressible(working.filterIndexed { i, _ -> i != idx }) }
            val sufficient = optional
                .filter { (idx, t) -> t.durationSec + t.gapBeforeSec + others(idx) >= deficit }
                .minByOrNull { it.value.durationSec }
            val victim = sufficient ?: optional.maxBy { it.value.durationSec }
            val (idx, task) = victim
            working = working.toMutableList().also { it.removeAt(idx) }
            changes += Change.Removed(task.title, task.durationSec)
            deficit -= task.durationSec + task.gapBeforeSec
        }
        if (deficit <= 0) return proposal(tasks, working, changes, total, availableSec)

        // 2. Proporcjonalne skracanie zadań opcjonalnych.
        val optionalIdx = working.indices.filter { !working[it].mandatory }
        val room = optionalIdx.associateWith { working[it].durationSec - minDurationOf(working[it]) }
        val totalRoom = room.values.sum()
        if (totalRoom <= 0) {
            return Result.Impossible(mandatorySec, availableSec, working)
        }
        val exact = optionalIdx.associateWith { room.getValue(it).toDouble() * deficit / totalRoom }
        val cuts = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        var remainder = deficit - cuts.values.sum()
        // Rozdziel resztę sekund według największych części ułamkowych.
        for (idx in optionalIdx.sortedByDescending { exact.getValue(it) - cuts.getValue(it) }) {
            if (remainder <= 0) break
            if (cuts.getValue(idx) < room.getValue(idx)) {
                cuts[idx] = cuts.getValue(idx) + 1
                remainder--
            }
        }
        for (idx in optionalIdx) {
            val cut = cuts.getValue(idx)
            if (cut <= 0) continue
            val t = working[idx]
            working[idx] = t.copy(durationSec = t.durationSec - cut)
            changes += Change.Shortened(t.title, t.durationSec, t.durationSec - cut)
        }
        return proposal(tasks, working, changes, total, availableSec)
    }

    private fun proposal(
        original: List<RoutineTask>,
        tasks: List<RoutineTask>,
        changes: List<Change>,
        total: Int,
        available: Int,
    ): Result {
        val newTotal = TimelineBuilder.totalSec(tasks)
        check(newTotal <= available) { "Planner produced $newTotal > $available" }
        check(
            original.filter { it.mandatory }.map { it.durationSec } ==
                tasks.filter { it.mandatory }.map { it.durationSec },
        ) { "Mandatory tasks must never change" }
        return Result.Proposal(tasks, changes, total, newTotal, available)
    }
}
