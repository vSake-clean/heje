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
import java.net.URLDecoder
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

    private val SECRET = Regex("^s-[A-Za-z0-9]+$")

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        val uri = runCatching { URI(if (text.startsWith("http")) text else "https://$text") }.getOrNull()
            ?: return Parsed.Unsupported
        val host = uri.host?.lowercase() ?: return Parsed.Unsupported
        if (host == "on.soundcloud.com" || host == "soundcloud.app.goo.gl") return Parsed.ShortLink(text)
        val query = parseQuery(uri.rawQuery)

        // Kod osadzenia / link odtwarzacza: w.soundcloud.com/player/?url=<adres>
        if (host == "w.soundcloud.com") {
            val inner = query["url"] ?: return Parsed.Unsupported
            return parse(inner)
        }
        // Adres API z kodu osadzenia, np. api.soundcloud.com/playlists/123?secret_token=s-abc
        if (host == "api.soundcloud.com" || host == "api-widget.soundcloud.com") {
            val seg = (uri.rawPath ?: "").split('/').filter { it.isNotBlank() }
            if (seg.size < 2 || seg[1].any { !it.isDigit() }) return Parsed.Unsupported
            val kind = when (seg[0]) {
                "tracks" -> MusicKind.TRACK
                "playlists" -> MusicKind.PLAYLIST
                else -> return Parsed.Unsupported
            }
            val token = query["secret_token"]?.takeIf { SECRET.matches(it) }
            val canonical = "https://api.soundcloud.com/${seg[0]}/${seg[1]}" + (token?.let { "?secret_token=$it" } ?: "")
            return Parsed.Supported(canonical, kind)
        }
        if (host != "soundcloud.com" && host != "www.soundcloud.com" && host != "m.soundcloud.com") {
            return Parsed.Unsupported
        }
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return Parsed.Unsupported
        val user = segments[0]
        if (user.lowercase() in RESERVED_FIRST_SEGMENTS) return Parsed.Unsupported
        // Prywatne treści mają kod dostępu: jako segment (…/s-abc) albo parametr (?secret_token=s-abc).
        val queryToken = query["secret_token"]?.takeIf { SECRET.matches(it) }
        return when {
            segments.size >= 3 && segments[1] == "sets" && segments[2].isNotBlank() -> {
                val token = segments.getOrNull(3)?.takeIf { SECRET.matches(it) } ?: queryToken
                Parsed.Supported(
                    "https://soundcloud.com/$user/sets/${segments[2]}" + (token?.let { "/$it" } ?: ""),
                    MusicKind.PLAYLIST,
                )
            }
            segments.size >= 2 && segments[1].lowercase() !in NON_TRACK_SECOND_SEGMENTS -> {
                val token = segments.getOrNull(2)?.takeIf { SECRET.matches(it) } ?: queryToken
                Parsed.Supported(
                    "https://soundcloud.com/$user/${segments[1]}" + (token?.let { "/$it" } ?: ""),
                    MusicKind.TRACK,
                )
            }
            else -> Parsed.Unsupported
        }
    }

    /** Czy link zawiera kod dostępu do prywatnej treści. */
    fun isSecret(url: String): Boolean = url.contains("/s-") || url.contains("secret_token=")

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, i), "UTF-8")
            val value = runCatching { URLDecoder.decode(part.substring(i + 1), "UTF-8") }.getOrDefault(part.substring(i + 1))
            key to value
        }.toMap()
    }

    /** Adres z atrybutu `src` ramki z oEmbed (parametr `url` odtwarzacza) – dokładnie ten, którego używa SoundCloud. */
    fun embedUrlFromOEmbedHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val src = Regex("""src="([^"]+)"""").find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        val uri = runCatching { URI(src) }.getOrNull() ?: return null
        if (uri.host?.lowercase() != "w.soundcloud.com") return null
        return parseQuery(uri.rawQuery)["url"]?.takeIf { it.startsWith("https://") }
    }

    /** Adres oficjalnego odtwarzacza (HTML5 Widget). */
    /**
     * Skrypt dla strony SoundCloud otwartej w przeglądarce wyboru: jeśli to Twoja prywatna playlista/utwór,
     * zwraca JSON {"u": link z kodem dostępu, "t": tytuł} z danych strony (adres odtwarzacza w metadanych,
     * dane strony mobilnej lub klasycznej). Pusty tekst, gdy treść jest publiczna albo nic nie znaleziono.
     */
    const val SECRET_LINK_JS = """(function(){try{
var norm=function(u){return String(u||'').split('?')[0].replace(/^https?:\/\/(m\.|www\.)?/,'').replace(/\/$/,'');};
var here=norm(location.href);
var same=function(p){p=norm(p);return !!p&&(here.indexOf(p)===0||p.indexOf(here)===0);};
var meta=function(n){var m=document.querySelector('meta[name="'+n+'"],meta[property="'+n+'"]');return m?(m.content||''):'';};
var title=meta('og:title')||meta('twitter:title')||document.title||'';
var out=function(u){return JSON.stringify({u:String(u),t:String(title)});};
var tp=meta('twitter:player');var tu=meta('twitter:url')||meta('og:url');
if(tp&&tp.indexOf('secret_token')>0&&(!tu||same(tu)))return out(tp);
var found='';
var walk=function(o,d){if(found||!o||typeof o!=='object'||d>14)return;
if(o.secret_token&&o.uri&&o.permalink_url&&same(o.permalink_url)){found=String(o.uri)+(String(o.uri).indexOf('?')<0?'?':'&')+'secret_token='+o.secret_token;if(o.title)title=o.title;return;}
if(o.secret_uri&&o.permalink_url&&same(o.permalink_url)){found=String(o.secret_uri);if(o.title)title=o.title;return;}
for(var k in o){if(Object.prototype.hasOwnProperty.call(o,k))walk(o[k],d+1);}};
walk(window.__NEXT_DATA__,0);if(found)return out(found);
walk(window.__sc_hydration,0);if(found)return out(found);
var c=[meta('og:url'),tu];var ln=document.querySelector('link[rel="canonical"]');if(ln)c.push(ln.href);
for(var j=0;j<c.length;j++){var u=String(c[j]||'');if(u.indexOf('/s-')>0||u.indexOf('secret_token=')>0)return out(u);}
}catch(e){}return '';})()"""

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
        /** @param warning ostrzeżenie, gdy nie udało się potwierdzić utworu, ale link wygląda poprawnie. */
        data class Ok(val selection: MusicSelection, val warning: String? = null) : Result
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
        // Link z kodem dostępu albo adres API z kodu osadzenia – odtwarzacz zagra go nawet bez metadanych.
        val secret = SoundCloudLinks.isSecret(supported.canonicalUrl) ||
            supported.canonicalUrl.startsWith("https://api.soundcloud.com/")
        val fallback = MusicSelection(
            url = supported.canonicalUrl,
            title = titleFromUrl(supported.canonicalUrl, supported.kind),
            kind = supported.kind,
            embedUrl = supported.canonicalUrl.takeIf { it.startsWith("https://api.soundcloud.com/") },
        )
        try {
            val conn = (URL(SoundCloudLinks.oEmbedUrl(supported.canonicalUrl)).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code == 404 || code == 403 || code == 401) {
                return@withContext if (secret) {
                    // Link z kodem dostępu – SoundCloud czasem nie udostępnia dla niego metadanych, ale odtwarzacz go zagra.
                    Result.Ok(fallback, "Nie udało się pobrać nazwy (prywatna treść). Link zawiera kod dostępu, więc powinien zagrać.")
                } else {
                    Result.Error(
                        "SoundCloud nie udostępnia tej treści. Jeśli to Twoja PRYWATNA playlista lub utwór: w SoundCloud wybierz " +
                            "„Udostępnij” → „Kopiuj link” (link prywatny zawiera kod „s-…”) i wklej go w zakładce „Wklej link”.",
                    )
                }
            }
            if (code !in 200..299) return@withContext Result.Error("SoundCloud odpowiedział błędem ($code). Spróbuj później.")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = OEmbedParser.parse(body)
            Result.Ok(
                MusicSelection(
                    url = supported.canonicalUrl,
                    title = OEmbedParser.cleanTitle(o.title, o.authorName).ifBlank { fallback.title },
                    artist = o.authorName.orEmpty(),
                    artworkUrl = o.thumbnailUrl,
                    kind = supported.kind,
                    embedUrl = SoundCloudLinks.embedUrlFromOEmbedHtml(o.html) ?: fallback.embedUrl,
                ),
            )
        } catch (e: java.io.IOException) {
            if (secret) {
                Result.Ok(fallback, "Brak połączenia – nie sprawdzono nazwy. Link zostanie użyty, gdy będzie internet.")
            } else {
                Result.Error("Brak połączenia z SoundCloud. Sprawdź internet i spróbuj ponownie.")
            }
        } catch (e: Exception) {
            Result.Error("Nie udało się odczytać informacji o utworze.")
        }
    }

    private fun titleFromUrl(url: String, kind: MusicKind): String {
        val slug = url.substringBefore('?').split('/').filter { it.isNotBlank() && !it.startsWith("s-") }.lastOrNull().orEmpty()
        val nice = slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
        return when {
            nice.isBlank() || nice.all { it.isDigit() } -> if (kind == MusicKind.PLAYLIST) "Prywatna playlista" else "Prywatny utwór"
            else -> nice
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
