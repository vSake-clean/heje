package pl.rytm.app.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.specMorningTasks

class TimelineTest {

    @Test
    fun `spec example timeline has exact start and end times`() {
        val e = TimelineBuilder.build(specMorningTasks())
        val ranges = e.map { it.startMs / 1000 to it.endMs / 1000 }
        // 00:10–00:25, 00:25–00:40, 00:40–01:40, 01:40–02:10, 02:10–05:10
        assertEquals(listOf(10L to 25L, 25L to 40L, 40L to 100L, 100L to 130L, 130L to 310L), ranges)
        assertEquals(310, TimelineBuilder.totalSec(specMorningTasks()))
    }

    @Test
    fun `tasks never overlap and next starts exactly when previous ends without gap`() {
        val e = TimelineBuilder.build(specMorningTasks())
        for (i in 1 until e.size) assertTrue(e[i].startMs >= e[i - 1].endMs)
        for (i in 1 until e.size) assertEquals(e[i - 1].endMs, e[i].startMs)
    }

    @Test
    fun `empty spoken text falls back to title`() {
        val e = TimelineBuilder.build(listOf(RoutineTask(title = "Pościel łóżko", spokenText = "", durationSec = 15)))
        assertEquals("Pościel łóżko", e[0].spokenText)
    }

    @Test
    fun `setStart rejects overlap with previous task`() {
        val t = specMorningTasks()
        val r = ScheduleEditing.setStart(t, 2, 30) // poprzednie kończy się o 40
        assertEquals(ScheduleEditing.StartChange.Overlap(40), r)
    }

    @Test
    fun `setStart creates a break and shifts following tasks`() {
        val t = specMorningTasks()
        val r = ScheduleEditing.setStart(t, 2, 55) as ScheduleEditing.StartChange.Ok
        assertEquals(15, r.tasks[2].gapBeforeSec)
        assertEquals(55, TimelineBuilder.startSecOf(r.tasks, 2))
        assertEquals(115, TimelineBuilder.startSecOf(r.tasks, 3))
    }

    @Test
    fun `shift never moves a task before the previous one ends`() {
        val t = ScheduleEditing.shift(specMorningTasks(), 1, -30)
        assertEquals(0, t[1].gapBeforeSec)
        assertEquals(25, TimelineBuilder.startSecOf(t, 1))
    }

    @Test
    fun `duplicate, move, remove and removeGaps`() {
        val t = specMorningTasks()
        val dup = ScheduleEditing.duplicate(t, 0)
        assertEquals(6, dup.size)
        assertEquals("Wstań z łóżka", dup[1].title)
        assertEquals(0, dup[1].gapBeforeSec)

        val moved = ScheduleEditing.move(t, 4, 0)
        assertEquals("Poranna higiena", moved[0].title)

        val removed = ScheduleEditing.remove(t, 0)
        assertEquals(4, removed.size)

        val packed = ScheduleEditing.removeGaps(t)
        assertEquals(300, TimelineBuilder.totalSec(packed))
    }
}
