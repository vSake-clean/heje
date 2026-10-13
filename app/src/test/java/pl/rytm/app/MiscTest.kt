package pl.rytm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.alarm.AlarmTimes
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.music.OEmbedParser
import pl.rytm.app.music.SoundCloudLinks
import pl.rytm.app.music.SoundCloudWidgetPlayer
import pl.rytm.app.speech.WavDuration
import pl.rytm.app.ui.home.HomeViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MiscTest {

    @Test
    fun `soundcloud links are classified`() {
        assertEquals(
            SoundCloudLinks.Parsed.Supported("https://soundcloud.com/forss/flickermood", MusicKind.TRACK),
            SoundCloudLinks.parse("https://soundcloud.com/forss/flickermood?si=abc&utm_source=clipboard"),
        )
        assertEquals(
            SoundCloudLinks.Parsed.Supported("https://soundcloud.com/user/sets/morning-mix", MusicKind.PLAYLIST),
            SoundCloudLinks.parse("https://m.soundcloud.com/user/sets/morning-mix"),
        )
        assertTrue(SoundCloudLinks.parse("https://on.soundcloud.com/AbCd1") is SoundCloudLinks.Parsed.ShortLink)
        assertEquals(SoundCloudLinks.Parsed.Unsupported, SoundCloudLinks.parse("https://soundcloud.com/search?q=lofi"))
        assertEquals(SoundCloudLinks.Parsed.Unsupported, SoundCloudLinks.parse("https://soundcloud.com/forss/likes"))
        assertEquals(SoundCloudLinks.Parsed.Unsupported, SoundCloudLinks.parse("https://example.com/forss/flickermood"))
        assertEquals(SoundCloudLinks.Parsed.Unsupported, SoundCloudLinks.parse("https://soundcloud.com/discover"))
    }

    @Test
    fun `link is extracted from shared text`() {
        val text = "Posłuchaj „Flickermood” Forss w #SoundCloud https://on.soundcloud.com/xYz12"
        assertEquals("https://on.soundcloud.com/xYz12", SoundCloudLinks.extractUrl(text))
        assertNull(SoundCloudLinks.extractUrl("brak linku"))
    }

    @Test
    fun `widget url uses the official player`() {
        val url = SoundCloudLinks.widgetUrl("https://soundcloud.com/forss/flickermood", autoPlay = true, startTrack = 2)
        assertTrue(url.startsWith("https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fforss%2Fflickermood"))
        assertTrue(url.contains("auto_play=true"))
        assertTrue(url.contains("start_track=2"))
    }

    @Test
    fun `oembed response parsing`() {
        val body = """{"version":1.0,"type":"rich","title":"Flickermood by Forss","author_name":"Forss",
            "thumbnail_url":"https://i1.sndcdn.com/a.jpg","html":"<iframe></iframe>","extra":1}"""
        val o = OEmbedParser.parse(body)
        assertEquals("Forss", o.authorName)
        assertEquals("Flickermood", OEmbedParser.cleanTitle(o.title, o.authorName))
    }

    @Test
    fun `js string escaping is safe`() {
        assertEquals("\"a\\\"b\\\\c\\u003c/script>\"", SoundCloudWidgetPlayer.jsString("a\"b\\c</script>"))
        assertEquals("75.0", SoundCloudWidgetPlayer.formatVolume(75f))
    }

    @Test
    fun `wav duration from header`() {
        val sampleRate = 22_050
        val seconds = 2
        val dataSize = sampleRate * 2 * seconds
        val b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataSize)
        assertEquals(2_000L, WavDuration.parseMs(b.array()))
        assertNull(WavDuration.parseMs(ByteArray(10)))
    }

    @Test
    fun `alarm next trigger respects days and time`() {
        val zone = ZoneId.of("Europe/Warsaw")
        // Poniedziałek 13.10.2025 07:00 → budzik 6:30 w dni robocze → wtorek 6:30.
        val now = ZonedDateTime.of(2025, 10, 13, 7, 0, 0, 0, zone)
        val next = AlarmTimes.next(now, 6 * 60 + 30, 0b0011111)!!
        assertEquals(LocalDateTime.of(2025, 10, 14, 6, 30), next.toLocalDateTime())
        // Piątek wieczorem → poniedziałek.
        val friday = ZonedDateTime.of(2025, 10, 17, 22, 0, 0, 0, zone)
        assertEquals(LocalDateTime.of(2025, 10, 20, 6, 30), AlarmTimes.next(friday, 390, 0b0011111)!!.toLocalDateTime())
        // Ten sam dzień, przed godziną budzika.
        val early = ZonedDateTime.of(2025, 10, 13, 5, 0, 0, 0, zone)
        assertEquals(LocalDateTime.of(2025, 10, 13, 6, 30), AlarmTimes.next(early, 390, 0b1111111)!!.toLocalDateTime())
        assertNull(AlarmTimes.next(now, 390, 0))
    }

    @Test
    fun `seconds until leave time`() {
        val now = LocalDateTime.of(2025, 10, 13, 7, 0)
        assertEquals(30 * 60, HomeViewModel.secondsUntil(7 * 60 + 30, now))
        assertNull(HomeViewModel.secondsUntil(6 * 60 + 30, now)) // jutro rano – ponad 16 h
    }
}
