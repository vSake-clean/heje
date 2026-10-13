package pl.rytm.app.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.domain.model.MusicSelection
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Rozpoznawanie linków SoundCloud. Obsługujemy utwory i playlisty (sety) – to, co oficjalny
 * odtwarzacz (HTML5 Widget) potrafi odtworzyć bez klucza API.
 */
object SoundCloudLinks {

    private val RESERVED_FIRST_SEGMENTS = setOf(
        "search", "discover", "stream", "you", "settings", "upload", "charts", "pages", "terms-of-use",
        "mobile", "signin", "signup", "logout", "notifications", "messages", "popular", "tags", "stations",
        "people", "jobs", "imprint", "premium", "go", "pro", "artists", "feed", "home", "library", "for-artists",
        "connect", "oauth", "login", "creators", "legal", "community-guidelines", "apps",
    )
    private val NON_TRACK_SECOND_SEGMENTS = setOf(
        "sets", "likes", "tracks", "albums", "reposts", "popular-tracks", "followers", "following",
        "comments", "spotlight", "playlists", "toptracks",
    )

    sealed interface Parsed {
        data class Supported(val canonicalUrl: String, val kind: MusicKind) : Parsed

        /** Krótki link (on.soundcloud.com) – wymaga rozwinięcia przekierowania. */
        data class ShortLink(val url: String) : Parsed
        data object Unsupported : Parsed
    }

    /** Wyciąga pierwszy link SoundCloud z dowolnego tekstu (np. udostępnionego z aplikacji SoundCloud). */
    fun extractUrl(text: String): String? {
        val regex = Regex("""https?://(?:[a-z0-9-]+\.)?soundcloud\.(?:com|app\.goo\.gl)[^\s"'<>]*""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
    }

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        val uri = runCatching { URI(if (text.startsWith("http")) text else "https://$text") }.getOrNull()
            ?: return Parsed.Unsupported
        val host = uri.host?.lowercase() ?: return Parsed.Unsupported
        if (host == "on.soundcloud.com" || host == "soundcloud.app.goo.gl") return Parsed.ShortLink(text)
        if (host != "soundcloud.com" && host != "www.soundcloud.com" && host != "m.soundcloud.com") {
            return Parsed.Unsupported
        }
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return Parsed.Unsupported
        val user = segments[0]
        if (user.lowercase() in RESERVED_FIRST_SEGMENTS) return Parsed.Unsupported
        return when {
            segments.size >= 3 && segments[1] == "sets" && segments[2].isNotBlank() ->
                Parsed.Supported("https://soundcloud.com/$user/sets/${segments[2]}", MusicKind.PLAYLIST)
            segments.size >= 2 && segments[1].lowercase() !in NON_TRACK_SECOND_SEGMENTS ->
                // Prywatne utwory mają dodatkowy segment z tokenem (s-xxxx) – zachowujemy go.
                Parsed.Supported(
                    "https://soundcloud.com/" + segments.take(if (segments.size >= 3 && segments[2].startsWith("s-")) 3 else 2)
                        .joinToString("/"),
                    MusicKind.TRACK,
                )
            else -> Parsed.Unsupported
        }
    }

    /** Adres oficjalnego odtwarzacza (HTML5 Widget). */
    fun widgetUrl(trackOrPlaylistUrl: String, autoPlay: Boolean, startTrack: Int = 0): String =
        buildString {
            append("https://w.soundcloud.com/player/?url=")
            append(URLEncoder.encode(trackOrPlaylistUrl, "UTF-8"))
            append("&auto_play=").append(autoPlay)
            append("&visual=false&show_comments=false&buying=false&sharing=false&download=false")
            append("&show_teaser=false&single_active=false&hide_related=true")
            if (startTrack > 0) append("&start_track=").append(startTrack)
        }

    fun oEmbedUrl(url: String): String =
        "https://soundcloud.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
}

@Serializable
data class OEmbedResponse(
    val title: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val html: String? = null,
)

object OEmbedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): OEmbedResponse = json.decodeFromString(OEmbedResponse.serializer(), body)

    /** Tytuł z oEmbed ma postać „Utwór by Autor” – zostawiamy sam tytuł utworu. */
    fun cleanTitle(title: String?, author: String?): String {
        val t = title?.trim().orEmpty()
        if (author.isNullOrBlank()) return t
        val suffix = " by ${author.trim()}"
        return if (t.endsWith(suffix)) t.removeSuffix(suffix) else t
    }
}

/**
 * Pobiera metadane utworu/playlisty przez publiczny endpoint oEmbed SoundCloud
 * (nie wymaga klucza API) i rozwija krótkie linki.
 */
class SoundCloudResolver(private val timeoutMs: Int = 10_000) {

    sealed interface Result {
        data class Ok(val selection: MusicSelection) : Result
        data class Error(val message: String) : Result
    }

    suspend fun resolve(input: String): Result = withContext(Dispatchers.IO) {
        val raw = SoundCloudLinks.extractUrl(input) ?: input.trim()
        var parsed = SoundCloudLinks.parse(raw)
        if (parsed is SoundCloudLinks.Parsed.ShortLink) {
            val expanded = runCatching { followRedirects(parsed.url) }.getOrNull()
                ?: return@withContext Result.Error("Nie udało się otworzyć skróconego linku. Sprawdź połączenie z internetem.")
            parsed = SoundCloudLinks.parse(expanded)
        }
        val supported = parsed as? SoundCloudLinks.Parsed.Supported
            ?: return@withContext Result.Error(
                "To nie jest link do utworu ani playlisty SoundCloud. Otwórz utwór lub playlistę i spróbuj ponownie.",
            )
        try {
            val conn = (URL(SoundCloudLinks.oEmbedUrl(supported.canonicalUrl)).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code == 404 || code == 403) {
                return@withContext Result.Error("Ten utwór jest niedostępny lub prywatny – SoundCloud nie pozwala go osadzić.")
            }
            if (code !in 200..299) return@withContext Result.Error("SoundCloud odpowiedział błędem ($code). Spróbuj później.")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = OEmbedParser.parse(body)
            Result.Ok(
                MusicSelection(
                    url = supported.canonicalUrl,
                    title = OEmbedParser.cleanTitle(o.title, o.authorName).ifBlank { supported.canonicalUrl },
                    artist = o.authorName.orEmpty(),
                    artworkUrl = o.thumbnailUrl,
                    kind = supported.kind,
                ),
            )
        } catch (e: java.io.IOException) {
            Result.Error("Brak połączenia z SoundCloud. Sprawdź internet i spróbuj ponownie.")
        } catch (e: Exception) {
            Result.Error("Nie udało się odczytać informacji o utworze.")
        }
    }

    private fun followRedirects(start: String): String {
        var url = start
        repeat(5) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && location != null) {
                url = URL(URL(url), location).toString()
                if (SoundCloudLinks.parse(url) is SoundCloudLinks.Parsed.Supported) return url
            } else {
                return url
            }
        }
        return url
    }
}
