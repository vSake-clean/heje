package pl.rytm.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.speech.AnnouncementTexts

class PolishNumbersTest {

    @Test
    fun `you have phrases`() {
        assertEquals("Masz trzydzieści sekund.", PolishNumbers.youHave(30))
        assertEquals("Masz piętnaście sekund.", PolishNumbers.youHave(15))
        assertEquals("Masz jedną minutę.", PolishNumbers.youHave(60))
        assertEquals("Masz dwie minuty.", PolishNumbers.youHave(120))
        assertEquals("Masz jedną minutę i trzydzieści sekund.", PolishNumbers.youHave(90))
        assertEquals("Masz pięć minut.", PolishNumbers.youHave(300))
        assertEquals("Masz dwadzieścia dwie sekundy.", PolishNumbers.youHave(22))
        assertEquals("Masz dwadzieścia jeden sekund.", PolishNumbers.youHave(21))
        assertEquals("Masz dwanaście sekund.", PolishNumbers.youHave(12))
        assertEquals("Masz jedną godzinę i pięć minut.", PolishNumbers.youHave(3900))
    }

    @Test
    fun `remaining phrases agree verb with number`() {
        assertEquals("Zostało dziesięć sekund.", PolishNumbers.remaining(10))
        assertEquals("Zostały dwie minuty.", PolishNumbers.remaining(120))
        assertEquals("Została jedna minuta.", PolishNumbers.remaining(60))
        assertEquals("Zostały trzy sekundy.", PolishNumbers.remaining(3))
    }

    @Test
    fun `task announcement composition`() {
        val cfg = AnnouncementConfig(includeDuration = true)
        assertEquals("Wstań z łóżka. Masz piętnaście sekund.", AnnouncementTexts.taskStart("Wstań z łóżka", 15, cfg))
        assertEquals("Teraz: Wstań z łóżka.", AnnouncementTexts.taskStart("Wstań z łóżka.", 15, AnnouncementConfig(), late = true))
    }

    @Test
    fun `duration parsing`() {
        assertEquals(90, TimeText.parseDuration("1:30"))
        assertEquals(90, TimeText.parseDuration("90"))
        assertEquals(3723, TimeText.parseDuration("1:02:03"))
        assertEquals(120, TimeText.parseDuration("2 min"))
        assertEquals(90, TimeText.parseDuration("1m30s"))
        assertEquals(45, TimeText.parseDuration("45 sek"))
        assertNull(TimeText.parseDuration("1:75"))
        assertNull(TimeText.parseDuration("abc"))
        assertNull(TimeText.parseDuration(""))
    }

    @Test
    fun `clock formatting`() {
        assertEquals("00:10", TimeText.clockSec(10))
        assertEquals("05:10", TimeText.clockSec(310))
        assertEquals("1:01:01", TimeText.clockSec(3661))
        assertEquals("00:01", TimeText.countdown(1))
        assertEquals("00:15", TimeText.countdown(14_001))
        assertEquals("06:30", TimeText.timeOfDay(390))
    }
}
