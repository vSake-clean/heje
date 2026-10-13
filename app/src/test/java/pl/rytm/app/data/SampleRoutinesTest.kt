package pl.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.domain.schedule.RoutinePlanner
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.schedule.ScheduleValidator
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.session.SessionPlan
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.domain.speech.SpeechCatalog

/** Przykładowe rutyny mają mieć sens: poprawny plan, realistyczne czasy, komunikaty mieszczące się w zadaniach. */
class SampleRoutinesTest {

    private val all = SampleRoutines.all()

    @Test
    fun `there are many distinct routines`() {
        assertTrue(all.size >= 8)
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertTrue(all.all { it.goal.isNotBlank() && it.tasks.isNotEmpty() })
    }

    @Test
    fun `every sample validates without errors or too long commands`() {
        all.forEach { r ->
            val issues = ScheduleValidator.validate(r.tasks, r.availableWindowSec, r.announcements, HeuristicSpeechEstimator())
            assertFalse("${r.name}: $issues", ScheduleValidator.hasErrors(issues))
            assertTrue("${r.name}: $issues", issues.none { it is ScheduleIssue.SpeechTooLong })
        }
    }

    @Test
    fun `routines with wake and leave time fit with some slack`() {
        all.filter { it.availableWindowSec != null }.forEach { r ->
            val fit = RoutinePlanner.fit(r.tasks, r.availableWindowSec!!)
            assertTrue("${r.name}: $fit", fit is RoutinePlanner.Result.Fits)
            assertTrue("${r.name} powinna mieć zapas", (fit as RoutinePlanner.Result.Fits).slackSec >= 60)
        }
    }

    @Test
    fun `durations are realistic`() {
        all.forEach { r ->
            r.tasks.forEach { t ->
                assertTrue("${r.name}/${t.title}: ${t.durationSec}s", t.durationSec in 15..1800)
                assertTrue("${r.name}/${t.title}: polecenie", t.announcementText.length in 5..90)
                t.minDurationSec?.let { assertTrue("${r.name}/${t.title}: min", it in 10 until t.durationSec + 1) }
            }
            val total = TimelineBuilder.totalSec(r.tasks)
            assertTrue("${r.name}: $total s", total in 4 * 60..75 * 60)
        }
        // Wybrane czynności – rozsądne zakresy.
        fun task(routine: String, title: String) = all.first { it.name == routine }.tasks.first { it.title == title }
        assertTrue(task("Poranek do szkoły lub pracy", "Umyj zęby").durationSec in 120..180)
        assertTrue(task("Poranek do szkoły lub pracy", "Śniadanie").durationSec in 600..1200)
        assertTrue(task("Poranek do szkoły lub pracy", "Prysznic").durationSec in 300..600)
        assertTrue(task("Nauka – dwa bloki skupienia", "Blok 1").durationSec == 25 * 60)
    }

    @Test
    fun `spec example is preserved at the start of the morning routine`() {
        val morning = SampleRoutines.morning()
        assertEquals(listOf(10, 25, 40, 100, 130), (0..4).map { TimelineBuilder.startSecOf(morning.tasks, it) })
        assertEquals(310, TimelineBuilder.endSecOf(morning.tasks, 4))
    }

    @Test
    fun `optional tasks exist where time may be short`() {
        val school = SampleRoutines.schoolOrWorkMorning()
        assertTrue(school.tasks.any { !it.mandatory })
        // 30 minut do wyjścia: prysznic wypada, śniadanie skrócone (nie usunięte), obowiązkowe bez zmian.
        val fit = RoutinePlanner.fit(school.tasks, 30 * 60) as RoutinePlanner.Result.Proposal
        assertTrue(fit.newTotalSec <= 30 * 60)
        val removed = fit.changes.filterIsInstance<RoutinePlanner.Change.Removed>().map { it.title }
        assertEquals(listOf("Prysznic"), removed)
        val breakfast = fit.tasks.single { it.title == "Śniadanie" }
        assertTrue(breakfast.durationSec in 600..899)
        assertEquals(
            school.tasks.filter { it.mandatory }.map { it.title to it.durationSec },
            fit.tasks.filter { it.mandatory }.map { it.title to it.durationSec },
        )
    }

    @Test
    fun `speech catalog covers every task command of every sample`() {
        all.forEach { r ->
            val plan = SessionPlan.from(r)
            val texts = SpeechCatalog.textsFor(plan)
            plan.entries.forEach { e -> assertTrue("${r.name}: ${e.spokenText}", texts.any { it.startsWith(e.spokenText.trim().removeSuffix(".")) }) }
            assertTrue(texts.all { it.isNotBlank() })
        }
    }
}
