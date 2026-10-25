package pl.rytm.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.data.SampleRoutines
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.schedule.ScheduleValidator
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator

class RoutineTextTest {

    @Test
    fun `built-in example parses cleanly`() {
        val r = RoutineText.parse(RoutineText.EXAMPLE)
        assertTrue(r.issues.toString(), r.issues.isEmpty())
        val routine = r.routine!!
        assertEquals("Poranek przed szkołą", routine.name)
        assertEquals(ScheduleMode.STRICT, routine.mode)
        assertEquals(6 * 60 + 40, routine.wakeTimeMinutes)
        assertEquals(7 * 60 + 25, routine.leaveTimeMinutes)
        assertTrue(routine.alarmEnabled)
        assertEquals(Routine.WEEKDAYS, routine.alarmDays)
        assertEquals(9, routine.tasks.size)
        assertEquals(20, routine.tasks[0].durationSec)
        assertEquals(10, routine.tasks[0].gapBeforeSec)
        assertEquals("Dzień dobry. Wstań z łóżka.", routine.tasks[0].spokenText)
        val bed = routine.tasks[1]
        assertEquals(90, bed.durationSec)
        assertFalse(bed.mandatory)
        assertEquals(45, bed.minDurationSec)
        assertEquals("Klucze, telefon, portfel, słuchawki.", routine.tasks[6].notes)
        assertTrue(routine.announcements.includeDuration)
        assertEquals(60, routine.announcements.warningBeforeEndSec)
        assertEquals("Czas wyjść z domu. Powodzenia!", routine.announcements.endMessage)
        // Przykład z polecenia dla AI ma sens: mieści się w oknie i komunikaty się mieszczą.
        val issues = ScheduleValidator.validate(routine.tasks, routine.availableWindowSec, routine.announcements, HeuristicSpeechEstimator())
        assertFalse(ScheduleValidator.hasErrors(issues))
        assertTrue(issues.none { it is ScheduleIssue.SpeechTooLong })
    }

    @Test
    fun `every sample routine survives text round trip`() {
        fun normalize(r: Routine) = r.copy(
            id = 0, music = null, musicPlayback = MusicPlayback.EMBEDDED, lastUsedAt = null,
            tasks = r.tasks.map { it.copy(id = 0) },
        )
        SampleRoutines.all().forEach { sample ->
            val text = RoutineText.format(sample)
            val parsed = RoutineText.parse(text)
            assertTrue("${sample.name}: ${parsed.issues}\n$text", parsed.issues.isEmpty())
            assertEquals(text, normalize(sample), normalize(parsed.routine!!))
        }
    }

    @Test
    fun `typical AI answer with extras is understood`() {
        val answer = """
            Oto Twoja rutyna:

            ```text
            **NAZWA:** Wieczór
            **TRYB:** elastyczny
            **CZEKANIE:** 1 minuta
            PO PRZERWANIU: wznów zadanie
            ZADANIA:
            1. 3 minuty | Zęby | Umyj zęby.
            2) półtorej minuty | Piżama | Przebierz się. | opcjonalne
            • 1,5 min | Książka | Poczytaj chwilę. | opcjonalne, min 30 sekund
            - 2 min Wywietrz pokój
            - Zgaś światło – 30 s
            ```
            Miłego wieczoru!
        """.trimIndent()
        val r = RoutineText.parse(answer)
        assertTrue(r.issues.toString(), r.errors.isEmpty())
        val routine = r.routine!!
        assertEquals("Wieczór", routine.name)
        assertEquals(ScheduleMode.FLEXIBLE, routine.mode)
        assertEquals(60, routine.flexibleTimeoutSec)
        assertEquals(InterruptionBehavior.RESUME_CURRENT_TASK, routine.interruptionBehavior)
        assertEquals(listOf(180, 90, 90, 120, 30), routine.tasks.map { it.durationSec })
        assertEquals(listOf("Zęby", "Piżama", "Książka", "Wywietrz pokój", "Zgaś światło"), routine.tasks.map { it.title })
        assertEquals(listOf(true, false, false, true, true), routine.tasks.map { it.mandatory })
        assertEquals(30, routine.tasks[2].minDurationSec)
        assertEquals("", routine.tasks[3].spokenText)
        assertEquals("Wywietrz pokój", routine.tasks[3].announcementText)
        // Tekst przed i po bloku kodu nie generuje ostrzeżeń.
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
    }

    @Test
    fun `errors point to the exact line`() {
        val text = """
            NAZWA: Test
            POBUDKA: rano
            ZADANIA:
            - 2 min | Ubierz się | Ubierz się.
            - Pościel łóżko | Pościel łóżko.
            - 5 | Woda | Wypij wodę.
            - 1 min | Biurko | Usiądź. | szybko
        """.trimIndent()
        val r = RoutineText.parse(text)
        val errors = r.errors.map { it.line }
        assertTrue(r.issues.toString(), 2 in errors) // „rano” to nie godzina
        assertTrue(r.issues.toString(), 5 in errors) // brak czasu
        assertFalse(r.canCreate)
        assertTrue(r.warnings.any { it.line == 6 && it.message.contains("bez jednostki") })
        assertTrue(r.warnings.any { it.line == 7 && it.message.contains("szybko") })
        assertEquals(listOf("Ubierz się", "Woda", "Biurko"), r.routine!!.tasks.map { it.title })
    }

    @Test
    fun `no tasks means nothing to create`() {
        val r = RoutineText.parse("NAZWA: Pusta\nTRYB: ścisły")
        assertNull(r.routine)
        assertFalse(r.canCreate)
        assertTrue(r.errors.isNotEmpty())
    }

    @Test
    fun `missing name gets a default and alarm without wake time is disabled`() {
        val r = RoutineText.parse("BUDZIK: tak\nZADANIA:\n- 1 min | Wstań")
        assertTrue(r.canCreate)
        assertEquals("Rutyna z tekstu", r.routine!!.name)
        assertFalse(r.routine!!.alarmEnabled)
        assertTrue(r.warnings.size >= 2)
    }

    @Test
    fun `notes - separate field, separate line and not inside spoken text`() {
        val r = RoutineText.parse(
            """
            NAZWA: N
            ZADANIA:
            - 2 min | Gotowanie | Uwaga: gorąca woda.
            - 1 min | Plecak | Spakuj plecak. | notatka: Zeszyt, piórnik
            - 3 min | Zęby | Umyj zęby.
            notatka: Pamiętaj o nitce.
            """.trimIndent(),
        )
        val t = r.routine!!.tasks
        assertEquals("Uwaga: gorąca woda.", t[0].spokenText)
        assertEquals("", t[0].notes)
        assertEquals("Zeszyt, piórnik", t[1].notes)
        assertEquals("Pamiętaj o nitce.", t[2].notes)
        assertEquals(3, t.size)
    }

    @Test
    fun `durations in many forms`() {
        fun d(s: String) = RoutineText.parseDuration(s)?.seconds
        assertEquals(15, d("15 s"))
        assertEquals(15, d("15 sekund"))
        assertEquals(120, d("2 min"))
        assertEquals(120, d("2 minuty"))
        assertEquals(300, d("5 minut"))
        assertEquals(90, d("1 min 30 s"))
        assertEquals(90, d("1 minuta i 30 sekund"))
        assertEquals(90, d("1:30"))
        assertEquals(90, d("1,5 min"))
        assertEquals(90, d("półtorej minuty"))
        assertEquals(30, d("pół minuty"))
        assertEquals(900, d("kwadrans"))
        assertEquals(3900, d("1 h 5 min"))
        assertEquals(3600, d("1 godzina"))
        assertTrue(RoutineText.parseDuration("45")!!.unitless)
        assertNull(d("chwila"))
        assertNull(d("2 min albo 3"))
    }

    @Test
    fun `times of day and days of week`() {
        assertEquals(390, RoutineText.parseTimeOfDay("6:30"))
        assertEquals(390, RoutineText.parseTimeOfDay("06.30"))
        assertEquals(420, RoutineText.parseTimeOfDay("7"))
        assertEquals(1125, RoutineText.parseTimeOfDay("18:45"))
        assertNull(RoutineText.parseTimeOfDay("25:00"))
        assertNull(RoutineText.parseTimeOfDay("rano"))

        assertEquals(Routine.WEEKDAYS, RoutineText.parseDays("pn-pt"))
        assertEquals(Routine.WEEKDAYS, RoutineText.parseDays("od poniedziałku do piątku"))
        assertEquals(Routine.ALL_DAYS, RoutineText.parseDays("codziennie"))
        assertEquals(0b1100000, RoutineText.parseDays("weekend"))
        assertEquals(0b0010101, RoutineText.parseDays("pn, śr, pt"))
        assertEquals(0b1100000, RoutineText.parseDays("sobota, niedziela"))
        assertNull(RoutineText.parseDays("czasami"))
    }

    @Test
    fun `routine detection for shared text`() {
        assertTrue(RoutineText.looksLikeRoutine(RoutineText.EXAMPLE))
        assertTrue(RoutineText.looksLikeRoutine("```\nNAZWA: X\n- 2 min | A | B.\n```"))
        assertFalse(RoutineText.looksLikeRoutine("Hej, co słychać?"))
        assertFalse(RoutineText.looksLikeRoutine("https://soundcloud.com/forss/flickermood"))
    }

    @Test
    fun `AI prompt contains rules, format, example and requirements`() {
        val p = RoutineText.aiPrompt("Wstaję 6:30, wychodzę 7:15, chcę zjeść śniadanie.")
        assertTrue(p.contains("FORMAT:"))
        assertTrue(p.contains(RoutineText.FORMAT_HELP))
        assertTrue(p.contains(RoutineText.EXAMPLE))
        assertTrue(p.endsWith("Wstaję 6:30, wychodzę 7:15, chcę zjeść śniadanie."))
        assertTrue(RoutineText.aiPrompt().contains("(opisz tutaj"))
        // Format i przykład w poleceniu muszą być zgodne z parserem.
        assertTrue(RoutineText.parse(RoutineText.EXAMPLE).canCreate)
    }

    @Test
    fun `exported text keeps timeline`() {
        val sample = SampleRoutines.schoolOrWorkMorning()
        val back = RoutineText.parse(RoutineText.format(sample)).routine!!
        assertEquals(TimelineBuilder.totalSec(sample.tasks), TimelineBuilder.totalSec(back.tasks))
        assertEquals(sample.availableWindowSec, back.availableWindowSec)
    }
}
