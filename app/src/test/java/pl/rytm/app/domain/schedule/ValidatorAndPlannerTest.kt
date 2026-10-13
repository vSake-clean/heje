package pl.rytm.app.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.domain.speech.SpeechDurationEstimator
import pl.rytm.app.specMorningTasks

class ValidatorAndPlannerTest {

    private val estimator = HeuristicSpeechEstimator()

    @Test
    fun `valid spec routine has no errors`() {
        val issues = ScheduleValidator.validate(specMorningTasks(), null, AnnouncementConfig(), estimator)
        assertFalse(ScheduleValidator.hasErrors(issues))
        assertTrue(issues.none { it is ScheduleIssue.SpeechTooLong })
    }

    @Test
    fun `exceeding wake to leave window is reported`() {
        val r = Routine(name = "x", tasks = specMorningTasks(), wakeTimeMinutes = 6 * 60, leaveTimeMinutes = 6 * 60 + 4)
        assertEquals(240, r.availableWindowSec)
        val issues = ScheduleValidator.validate(r.tasks, r.availableWindowSec, r.announcements, estimator)
        val ex = issues.filterIsInstance<ScheduleIssue.ExceedsWindow>().single()
        assertEquals(70, ex.overBySec)
    }

    @Test
    fun `window across midnight`() {
        val r = Routine(name = "x", wakeTimeMinutes = 23 * 60 + 50, leaveTimeMinutes = 10)
        assertEquals(20 * 60, r.availableWindowSec)
    }

    @Test
    fun `too long voice message for a short task is detected at planning time`() {
        val tasks = listOf(
            RoutineTask(title = "A", spokenText = "To jest bardzo długi komunikat, który na pewno nie zmieści się w trzech sekundach zadania.", durationSec = 3),
            RoutineTask(title = "B", spokenText = "Dalej.", durationSec = 10),
        )
        val issues = ScheduleValidator.validate(tasks, null, AnnouncementConfig(), estimator)
        val issue = issues.filterIsInstance<ScheduleIssue.SpeechTooLong>().single()
        assertEquals(0, issue.taskIndex)
        assertTrue(issue.estimatedMs > issue.availableMs)
    }

    @Test
    fun `measured durations override heuristic`() {
        val tasks = listOf(RoutineTask(title = "A", spokenText = "Krótko.", durationSec = 2), RoutineTask(title = "B", durationSec = 5))
        val slow = SpeechDurationEstimator { 5_000 }
        assertTrue(ScheduleValidator.validate(tasks, null, AnnouncementConfig(), slow).any { it is ScheduleIssue.SpeechTooLong })
    }

    @Test
    fun `planner fits when enough time`() {
        val r = RoutinePlanner.fit(specMorningTasks(), 400)
        assertEquals(RoutinePlanner.Result.Fits(310, 90), r)
    }

    @Test
    fun `planner removes breaks first`() {
        val r = RoutinePlanner.fit(specMorningTasks(), 305) as RoutinePlanner.Result.Proposal
        assertEquals(305, r.newTotalSec)
        assertTrue(r.changes.all { it is RoutinePlanner.Change.GapReduced })
    }

    @Test
    fun `planner shortens only optional tasks and never touches mandatory ones`() {
        val tasks = listOf(
            RoutineTask(title = "Wstań", durationSec = 60),
            RoutineTask(title = "Pościel łóżko", durationSec = 120, mandatory = false),
            RoutineTask(title = "Śniadanie", durationSec = 600),
            RoutineTask(title = "Plecak", durationSec = 180, mandatory = false),
        )
        // 960 s planu, dostępne 30 min = 1800 → mieści się; 15 min = 900 → brakuje 60 s.
        val r = RoutinePlanner.fit(tasks, 900) as RoutinePlanner.Result.Proposal
        assertEquals(900, r.newTotalSec)
        assertEquals(60, r.tasks[0].durationSec)
        assertEquals(600, r.tasks[2].durationSec)
        assertTrue(r.changes.all { it is RoutinePlanner.Change.Shortened })
        assertTrue(r.tasks[1].durationSec >= RoutinePlanner.minDurationOf(tasks[1]))
    }

    @Test
    fun `planner removes optional tasks when shortening is not enough`() {
        val tasks = listOf(
            RoutineTask(title = "Wstań", durationSec = 60),
            RoutineTask(title = "Pościel łóżko", durationSec = 120, mandatory = false),
            RoutineTask(title = "Śniadanie", durationSec = 600),
            RoutineTask(title = "Plecak", durationSec = 180, mandatory = false),
        )
        val r = RoutinePlanner.fit(tasks, 700) as RoutinePlanner.Result.Proposal
        assertTrue(r.newTotalSec <= 700)
        assertTrue(r.changes.any { it is RoutinePlanner.Change.Removed })
        assertEquals(listOf(60, 600), r.tasks.filter { it.mandatory }.map { it.durationSec })
    }

    @Test
    fun `planner reports impossible when mandatory tasks alone exceed the window`() {
        val tasks = listOf(RoutineTask(title = "A", durationSec = 600), RoutineTask(title = "B", durationSec = 120, mandatory = false))
        val r = RoutinePlanner.fit(tasks, 500)
        assertTrue(r is RoutinePlanner.Result.Impossible)
        assertEquals(600, (r as RoutinePlanner.Result.Impossible).mandatorySec)
    }

    @Test
    fun `thirty minutes until leaving produces a plan of at most thirty minutes`() {
        val tasks = (1..9).map { RoutineTask(title = "Z$it", durationSec = 300, mandatory = it % 3 == 0) } // 45 min
        val r = RoutinePlanner.fit(tasks, 30 * 60) as RoutinePlanner.Result.Proposal
        assertTrue(r.newTotalSec <= 30 * 60)
        assertEquals(3, r.tasks.count { it.mandatory })
        assertTrue(r.tasks.filter { it.mandatory }.all { it.durationSec == 300 })
    }
}
