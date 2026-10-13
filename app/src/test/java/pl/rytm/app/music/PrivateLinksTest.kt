package pl.rytm.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.music.SoundCloudLinks.Parsed

class PrivateLinksTest {

    @Test
    fun `private playlist link keeps its secret token`() {
        assertEquals(
            Parsed.Supported("https://soundcloud.com/ania/sets/poranek/s-AbC123xYz", MusicKind.PLAYLIST),
            SoundCloudLinks.parse("https://soundcloud.com/ania/sets/poranek/s-AbC123xYz?si=e5f&utm_source=clipboard"),
        )
        assertTrue(SoundCloudLinks.isSecret("https://soundcloud.com/ania/sets/poranek/s-AbC123xYz"))
    }

    @Test
    fun `private track link keeps its secret token`() {
        assertEquals(
            Parsed.Supported("https://soundcloud.com/ania/demo/s-QwE987", MusicKind.TRACK),
            SoundCloudLinks.parse("https://soundcloud.com/ania/demo/s-QwE987"),
        )
    }

    @Test
    fun `secret token given as query parameter`() {
        assertEquals(
            Parsed.Supported("https://soundcloud.com/ania/sets/poranek/s-ZZ11", MusicKind.PLAYLIST),
            SoundCloudLinks.parse("https://soundcloud.com/ania/sets/poranek?secret_token=s-ZZ11"),
        )
    }

    @Test
    fun `embed code and api links are supported`() {
        val embed = "https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com/playlists/123456%3Fsecret_token%3Ds-AbC" +
            "&color=%23ff5500&auto_play=false"
        assertEquals(
            Parsed.Supported("https://api.soundcloud.com/playlists/123456?secret_token=s-AbC", MusicKind.PLAYLIST),
            SoundCloudLinks.parse(embed),
        )
        assertEquals(
            Parsed.Supported("https://api.soundcloud.com/tracks/293", MusicKind.TRACK),
            SoundCloudLinks.parse("https://api.soundcloud.com/tracks/293"),
        )
        assertEquals(Parsed.Unsupported, SoundCloudLinks.parse("https://api.soundcloud.com/users/1"))
        // Link osadzenia wyciągnięty z udostępnionego tekstu.
        assertEquals("https://w.soundcloud.com/player/?url=x", SoundCloudLinks.extractUrl("kod: https://w.soundcloud.com/player/?url=x"))
    }

    @Test
    fun `public links are not treated as secret`() {
        assertFalse(SoundCloudLinks.isSecret("https://soundcloud.com/forss/sets/soulhack"))
        assertEquals(
            Parsed.Supported("https://soundcloud.com/forss/sets/soulhack", MusicKind.PLAYLIST),
            SoundCloudLinks.parse("https://soundcloud.com/forss/sets/soulhack/"),
        )
    }

    @Test
    fun `embed url is read from oEmbed html exactly as SoundCloud provides it`() {
        val html = "<iframe width=\"100%\" height=\"450\" scrolling=\"no\" frameborder=\"no\" allow=\"autoplay; encrypted-media\" " +
            "src=\"https://w.soundcloud.com/player/?visual=true&url=https%3A%2F%2Fapi.soundcloud.com%2Fplaylists%2F18&show_artwork=true\"></iframe>"
        assertEquals("https://api.soundcloud.com/playlists/18", SoundCloudLinks.embedUrlFromOEmbedHtml(html))
        val secret = html.replace("playlists%2F18", "playlists%2F18%3Fsecret_token%3Ds-XyZ")
        assertEquals("https://api.soundcloud.com/playlists/18?secret_token=s-XyZ", SoundCloudLinks.embedUrlFromOEmbedHtml(secret))
        assertNull(SoundCloudLinks.embedUrlFromOEmbedHtml("<iframe src=\"https://evil.example/player?url=x\"></iframe>"))
        assertNull(SoundCloudLinks.embedUrlFromOEmbedHtml(null))
    }

    @Test
    fun `widget url for a private playlist matches the official embed format`() {
        val url = SoundCloudLinks.widgetUrl("https://api.soundcloud.com/playlists/18?secret_token=s-XyZ", autoPlay = true)
        assertTrue(url, url.startsWith("https://w.soundcloud.com/player/?url=https%3A%2F%2Fapi.soundcloud.com%2Fplaylists%2F18%3Fsecret_token%3Ds-XyZ"))
    }
}
