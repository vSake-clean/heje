package pl.rytm.app.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.session.MusicOutput
import pl.rytm.app.domain.session.MusicState
import pl.rytm.app.domain.session.MusicStatus
import pl.rytm.app.domain.session.SessionPlan
import java.util.Locale

/**
 * Odtwarzanie muzyki przez OFICJALNY odtwarzacz SoundCloud (HTML5 Widget) osadzony w WebView.
 *
 * - Nie wymaga klucza API ani logowania; odtwarza to, co SoundCloud pozwala osadzać.
 * - Głośność jest ustawiana metodą widżetu `setVolume(0..100)` – to głośność samego odtwarzacza,
 *   więc przyciszanie podczas komunikatów nie zmienia głośności systemowej.
 * - Odtwarzacz jest pokazywany na ekranie sesji (z logo i linkiem SoundCloud), a poza nim
 *   „parkowany” w niewidocznym miejscu aktywności – strona pozostaje dla WebView widoczna,
 *   więc muzyka gra dalej przy wygaszonym ekranie i w słuchawkach.
 * - Muzyka nigdy nie jest celowo zatrzymywana na czas komunikatów – tylko przyciszana.
 *   [PlaybackGuard] wznawia ją, gdyby system/słuchawki/inna aplikacja ją wstrzymały.
 * - Po utracie internetu odtwarzacz czeka na sieć i wznawia od zapamiętanego miejsca.
 *   Harmonogram działa niezależnie od muzyki.
 *
 * Wszystkie operacje na WebView wykonywane są w wątku głównym.
 */
@SuppressLint("SetJavaScriptEnabled")
class SoundCloudWidgetPlayer(context: Context) : MusicOutput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val contextWrapper = MutableContextWrapper(appContext)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var webView: WebView? = null
    private var pendingContainer: java.lang.ref.WeakReference<ViewGroup>? = null
    private var parking: java.lang.ref.WeakReference<ViewGroup>? = null
    private val guard = PlaybackGuard()
    private val _state = MutableStateFlow(MusicState(playback = MusicPlayback.EMBEDDED, volumeControllable = true))
    override val state: StateFlow<MusicState> = _state.asStateFlow()

    private var plan: SessionPlan? = null
    @Volatile private var wantPlaying = false
    @Volatile private var volume = 100f
    private var lastSentVolume = -1f
    private var currentIndex = 0
    private var lastPositionMs = 0L
    private var lastProgressAt = 0L
    private var resumeSeekMs: Long? = null
    private var widgetReady = false
    private var released = false
    private var retryAttempt = 0
    private var networkAvailable = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val html: String by lazy {
        appContext.assets.open(ASSET).bufferedReader().use { it.readText() }
    }

    // ------------------------------------------------------------------ MusicOutput

    override fun prepare(plan: SessionPlan, autoPlay: Boolean) {
        main.post {
            this.plan = plan
            wantPlaying = autoPlay
            volume = plan.musicVolume.toFloat()
            released = false
            val music = plan.music
            if (music == null) {
                _state.value = MusicState(playback = MusicPlayback.NONE)
                return@post
            }
            _state.value = MusicState(
                playback = MusicPlayback.EMBEDDED,
                status = MusicStatus.LOADING,
                title = music.title,
                artist = music.artist,
                artworkUrl = music.artworkUrl,
                permalinkUrl = music.url,
                volumeControllable = true,
            )
            ensureWebView()
            registerNetworkCallback()
            loadPage()
            guard.reset()
            main.removeCallbacks(probeLoop)
            main.postDelayed(probeLoop, PROBE_INTERVAL_MS)
        }
    }

    override fun play() {
        main.post {
            wantPlaying = true
            if (released) return@post
            when {
                widgetReady -> js("rytm.play()")
                _state.value.status == MusicStatus.ERROR || _state.value.status == MusicStatus.WAITING_FOR_NETWORK -> reload()
            }
        }
    }

    override fun pause() {
        main.post {
            wantPlaying = false
            if (widgetReady) js("rytm.pause()")
            if (_state.value.status == MusicStatus.PLAYING || _state.value.status == MusicStatus.LOADING) {
                _state.update { it.copy(status = MusicStatus.PAUSED) }
            }
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 100f)
        main.post { applyVolume() }
    }

    override fun stop() {
        main.post {
            main.removeCallbacks(probeLoop)
            released = true
            wantPlaying = false
            if (widgetReady) js("rytm.pause()")
            unregisterNetworkCallback()
            destroyWebView()
            _state.update { it.copy(status = MusicStatus.NONE) }
        }
    }

    /** Ponów wczytanie (np. przycisk „Spróbuj ponownie”). */
    fun retry() = main.post { if (!released) reload() }

    // ------------------------------------------------------------------ wyświetlanie w interfejsie

    /** Pokazuje odtwarzacz w podanym kontenerze (ekran sesji). Odtwarzanie trwa także po odłączeniu. */
    fun attachTo(container: ViewGroup) {
        val view = webView
        if (view == null) {
            // Odtwarzacz jeszcze się tworzy – podłącz go, gdy będzie gotowy.
            pendingContainer = java.lang.ref.WeakReference(container)
            return
        }
        pendingContainer = null
        if (view.parent === container) return
        contextWrapper.baseContext = container.context
        (view.parent as? ViewGroup)?.removeView(view)
        container.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Ekran sesji zniknął – odtwarzacz wraca na „parking”, żeby WebView wciąż był w oknie aktywności. */
    fun detachFrom(container: ViewGroup) {
        if (pendingContainer?.get() === container) pendingContainer = null
        val view = webView ?: return
        if (view.parent !== container) return
        container.removeView(view)
        val park = parking?.get()
        if (park != null && park !== container) attachTo(park) else contextWrapper.baseContext = appContext
    }

    /**
     * Niewidoczne miejsce w oknie aktywności (1×1 px). Dopóki WebView jest w oknie, Chromium traktuje
     * odtwarzacz jak aktywną kartę – bez wstrzymywania i dławienia przy wygaszonym ekranie.
     */
    fun park(holder: ViewGroup) {
        parking = java.lang.ref.WeakReference(holder)
        val view = webView
        if (view == null) {
            if (pendingContainer?.get() == null) pendingContainer = java.lang.ref.WeakReference(holder)
            return
        }
        if (view.parent == null) attachTo(holder)
    }

    fun unpark(holder: ViewGroup) {
        if (parking?.get() === holder) parking = null
        if (pendingContainer?.get() === holder) pendingContainer = null
        val view = webView ?: return
        if (view.parent === holder) {
            holder.removeView(view)
            contextWrapper.baseContext = appContext
        }
    }

    val hasView: Boolean get() = webView != null

    // ------------------------------------------------------------------ WebView

    private fun ensureWebView() {
        if (webView != null) return
        val view = KeepAliveWebView(contextWrapper)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
        }
        // Proces renderujący ma pozostać ważny także przy wygaszonym ekranie – inaczej system mógłby go ubić.
        view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        view.setBackgroundColor(Color.TRANSPARENT)
        // Zalogowany w Rytmie użytkownik SoundCloud: odtwarzacz może użyć sesji (np. własne prywatne playlisty).
        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(view, true) }
        view.addJavascriptInterface(Bridge(), "RytmBridge")
        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                // Kliknięcie logo/tytułu w odtwarzaczu: otwórz SoundCloud poza aplikacją, nie podmieniaj strony odtwarzacza.
                if (request.isForMainFrame) {
                    openExternally(request.url)
                    return true
                }
                return false
            }

            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "WebView renderer gone (crash=${detail.didCrash()}) – odtwarzam odtwarzacz")
                destroyWebView()
                if (!released) {
                    resumeSeekMs = lastPositionMs
                    main.postDelayed({
                        if (!released) {
                            ensureWebView()
                            loadPage()
                        }
                    }, 1_000)
                }
                return true
            }
        }
        webView = view
        view.onResume()
        view.resumeTimers()
        (pendingContainer?.get() ?: parking?.get())?.let { attachTo(it) }
    }

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        widgetReady = false
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.removeJavascriptInterface("RytmBridge")
        view.destroy()
    }

    private fun loadPage() {
        val view = webView ?: return
        widgetReady = false
        lastSentVolume = -1f
        view.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null)
    }

    private fun reload() {
        if (released) return
        resumeSeekMs = if (plan?.music?.kind == MusicKind.TRACK || lastPositionMs > 0) lastPositionMs else null
        _state.update { it.copy(status = MusicStatus.LOADING, message = null) }
        ensureWebView()
        loadPage()
    }

    private fun onPageLoaded() {
        val music = plan?.music ?: return
        // Adres osadzenia z oEmbed (zawiera kod dostępu prywatnych playlist) albo zwykły link.
        val url = SoundCloudLinks.widgetUrl(music.embedUrl ?: music.url, autoPlay = wantPlaying, startTrack = currentIndex)
        js(
            "rytm.load(${jsString(url)}, $wantPlaying, ${formatVolume(volume)}, ${plan?.musicLoop ?: true})",
        )
        lastSentVolume = volume
    }

    private fun applyVolume() {
        if (!widgetReady) return
        val v = volume
        if (lastSentVolume >= 0 && kotlin.math.abs(v - lastSentVolume) < 0.1f) return
        lastSentVolume = v
        js("rytm.setVolume(${formatVolume(v)})")
    }

    private fun js(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    private fun openExternally(uri: Uri) {
        runCatching {
            appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun scheduleRetry() {
        if (released || !networkAvailable) return
        val delay = RETRY_DELAYS_MS[retryAttempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        retryAttempt++
        main.postDelayed({
            val s = _state.value.status
            if (!released && (s == MusicStatus.ERROR || s == MusicStatus.WAITING_FOR_NETWORK)) reload()
        }, delay)
    }

    // ------------------------------------------------------------------ strażnik odtwarzania

    private val probeLoop = object : Runnable {
        override fun run() {
            if (released) return
            if (wantPlaying && networkAvailable) {
                if (widgetReady) js("rytm.probe()") else handleProbe(ready = false, paused = true, position = 0)
            } else {
                guard.reset()
            }
            main.postDelayed(this, PROBE_INTERVAL_MS)
        }
    }

    private fun handleProbe(ready: Boolean, paused: Boolean, position: Long) {
        if (released) return
        val busy = _state.value.status == MusicStatus.ERROR || _state.value.status == MusicStatus.WAITING_FOR_NETWORK
        if (busy) return // tym zajmuje się ponawianie po błędzie / po odzyskaniu sieci
        when (guard.onProbe(SystemClock.elapsedRealtime(), wantPlaying, networkAvailable, ready, paused, position)) {
            PlaybackGuard.Action.NONE -> Unit
            PlaybackGuard.Action.PLAY -> {
                Log.i(TAG, "Muzyka niespodziewanie stoi – wznawiam")
                js("rytm.play()")
            }
            PlaybackGuard.Action.RELOAD -> {
                Log.w(TAG, "Odtwarzacz nie reaguje – przeładowuję od ${lastPositionMs} ms")
                reload()
            }
        }
    }

    // ------------------------------------------------------------------ sieć

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        networkAvailable = cm.activeNetwork?.let { n ->
            cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.post {
                    val wasOffline = !networkAvailable
                    networkAvailable = true
                    val s = _state.value.status
                    if (!released && (wasOffline || s == MusicStatus.ERROR) &&
                        (s == MusicStatus.WAITING_FOR_NETWORK || s == MusicStatus.ERROR)
                    ) {
                        retryAttempt = 0
                        main.postDelayed({ if (!released) reload() }, 1_500)
                    }
                }
            }

            override fun onLost(network: Network) {
                main.post {
                    networkAvailable = false
                    main.postDelayed(::checkStall, 5_000)
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching { appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }

    /** Bez sieci i bez postępu odtwarzania → czekamy na internet (harmonogram działa dalej). */
    private fun checkStall() {
        if (released || networkAvailable) return
        val stalled = SystemClock.elapsedRealtime() - lastProgressAt > 4_000
        if (wantPlaying && stalled) {
            _state.update {
                it.copy(status = MusicStatus.WAITING_FOR_NETWORK, message = "Brak internetu – muzyka wróci po odzyskaniu połączenia.")
            }
        } else if (wantPlaying) {
            main.postDelayed(::checkStall, 3_000)
        }
    }

    // ------------------------------------------------------------------ most JS → Kotlin

    @Serializable
    private data class SoundInfo(
        val title: String = "",
        val artist: String = "",
        val artwork: String = "",
        val permalink: String = "",
        val duration: Long = 0,
    )

    @Serializable
    private data class Progress(val position: Double = 0.0, val relative: Double = 0.0)

    @Serializable
    private data class Probe(val ready: Boolean = false, val paused: Boolean = true, val position: Long = 0)

    private inner class Bridge {
        @JavascriptInterface
        fun onPageLoaded(@Suppress("UNUSED_PARAMETER") data: String) = main.post { onPageLoaded() }

        @JavascriptInterface
        fun onApiError(@Suppress("UNUSED_PARAMETER") data: String) = main.post {
            _state.update {
                if (!networkAvailable) {
                    it.copy(status = MusicStatus.WAITING_FOR_NETWORK, message = "Brak internetu – muzyka wróci po odzyskaniu połączenia.")
                } else {
                    it.copy(status = MusicStatus.ERROR, message = "Nie udało się wczytać odtwarzacza SoundCloud. Ponawiam…")
                }
            }
            scheduleRetry()
        }

        @JavascriptInterface
        fun onReady(@Suppress("UNUSED_PARAMETER") data: String) = main.post {
            widgetReady = true
            retryAttempt = 0
            lastSentVolume = -1f
            applyVolume()
            resumeSeekMs?.takeIf { it > 0 }?.let { js("rytm.seekTo($it)") }
            resumeSeekMs = null
            _state.update {
                it.copy(status = if (wantPlaying) MusicStatus.LOADING else MusicStatus.PAUSED, message = null)
            }
            if (wantPlaying) js("rytm.play()") else js("rytm.pause()")
        }

        @JavascriptInterface
        fun onPlay(@Suppress("UNUSED_PARAMETER") data: String) = main.post {
            lastProgressAt = SystemClock.elapsedRealtime()
            if (!wantPlaying) {
                // Ktoś nacisnął „play” w widżecie, gdy sesja jest wstrzymana – szanujemy stan sesji.
                js("rytm.pause()")
                return@post
            }
            _state.update { it.copy(status = MusicStatus.PLAYING, message = null) }
        }

        @JavascriptInterface
        fun onPause(@Suppress("UNUSED_PARAMETER") data: String) = main.post {
            val current = _state.value.status
            if (current == MusicStatus.WAITING_FOR_NETWORK || current == MusicStatus.INTERRUPTED) return@post
            _state.update { it.copy(status = MusicStatus.PAUSED) }
            // Pauza, o którą sesja nie prosiła (system, słuchawki, inna aplikacja) – sprawdź i wznów.
            if (wantPlaying) main.postDelayed({ if (wantPlaying && widgetReady && !released) js("rytm.probe()") }, 1_000)
        }

        @JavascriptInterface
        fun onFinish(@Suppress("UNUSED_PARAMETER") data: String) = main.post {
            _state.update { it.copy(status = MusicStatus.PAUSED, message = "Muzyka dobiegła końca.") }
        }

        @JavascriptInterface
        fun onSound(data: String) = main.post {
            val info = runCatching { json.decodeFromString(SoundInfo.serializer(), data) }.getOrNull() ?: return@post
            _state.update {
                it.copy(
                    title = info.title.ifBlank { it.title },
                    artist = info.artist.ifBlank { it.artist },
                    artworkUrl = info.artwork.ifBlank { it.artworkUrl },
                    permalinkUrl = info.permalink.ifBlank { it.permalinkUrl },
                    durationMs = info.duration,
                )
            }
        }

        @JavascriptInterface
        fun onIndex(data: String) = main.post { data.toIntOrNull()?.let { currentIndex = it } }

        @JavascriptInterface
        fun onProgress(data: String) = main.post {
            val p = runCatching { json.decodeFromString(Progress.serializer(), data) }.getOrNull() ?: return@post
            val position = p.position.toLong()
            lastPositionMs = position
            lastProgressAt = SystemClock.elapsedRealtime()
            _state.update {
                if (it.status == MusicStatus.WAITING_FOR_NETWORK || it.status == MusicStatus.LOADING) {
                    it.copy(positionMs = position, status = MusicStatus.PLAYING, message = null)
                } else {
                    it.copy(positionMs = position)
                }
            }
        }

        @JavascriptInterface
        fun onError(@Suppress("UNUSED_PARAMETER") data: String) = main.post {
            _state.update {
                it.copy(
                    status = if (networkAvailable) MusicStatus.ERROR else MusicStatus.WAITING_FOR_NETWORK,
                    message = if (networkAvailable) {
                        "SoundCloud nie może odtworzyć tego utworu (może być niedostępny do osadzania). Ponawiam…"
                    } else {
                        "Brak internetu – muzyka wróci po odzyskaniu połączenia."
                    },
                )
            }
            scheduleRetry()
        }

        @JavascriptInterface
        fun onProbe(data: String) = main.post {
            val p = runCatching { json.decodeFromString(Probe.serializer(), data) }.getOrNull() ?: return@post
            if (p.ready && !p.paused) {
                lastPositionMs = p.position
                lastProgressAt = SystemClock.elapsedRealtime()
                if (_state.value.status != MusicStatus.PLAYING && wantPlaying) {
                    _state.update { it.copy(status = MusicStatus.PLAYING, message = null) }
                }
            }
            handleProbe(p.ready, p.paused, p.position)
        }

        @JavascriptInterface
        fun onVolume(@Suppress("UNUSED_PARAMETER") data: String) = Unit
    }

    companion object {
        private const val TAG = "SoundCloudWidget"
        private const val ASSET = "soundcloud_player.html"

        /** Adres bazowy strony odtwarzacza – zarezerwowana domena dla treści aplikacji (nie podszywamy się pod SoundCloud). */
        const val BASE_URL = "https://appassets.androidplatform.net/player/"
        private val RETRY_DELAYS_MS = longArrayOf(3_000, 10_000, 30_000, 60_000)
        private const val PROBE_INTERVAL_MS = 3_000L

        fun formatVolume(v: Float): String = String.format(Locale.ROOT, "%.1f", v.coerceIn(0f, 100f))

        fun jsString(s: String): String = buildString {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '<' -> append("\\u003c")
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}

/**
 * WebView odtwarzacza muzyki. Gdy okno aktywności staje się niewidoczne (wygaszony ekran,
 * aplikacja w tle), WebView dalej zgłasza się jako widoczny – inaczej Chromium traktowałby stronę
 * jak kartę w tle (wstrzymywanie/dławienie), a muzyka mogłaby ucichnąć w słuchawkach.
 */
class KeepAliveWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

/**
 * Tryb „aplikacja SoundCloud”: muzyka gra w oficjalnej aplikacji SoundCloud. Rytm nie ma dostępu
 * do jej głośności ani odtwarzania – pokazuje to uczciwie w interfejsie. Harmonogram działa normalnie.
 */
class ExternalSoundCloudOutput : MusicOutput {
    private val _state = MutableStateFlow(MusicState(playback = MusicPlayback.EXTERNAL_APP))
    override val state: StateFlow<MusicState> = _state.asStateFlow()

    override fun prepare(plan: SessionPlan, autoPlay: Boolean) {
        val m = plan.music
        _state.value = MusicState(
            playback = MusicPlayback.EXTERNAL_APP,
            status = MusicStatus.EXTERNAL,
            title = m?.title,
            artist = m?.artist,
            artworkUrl = m?.artworkUrl,
            permalinkUrl = m?.url,
            message = "Muzyką steruje aplikacja SoundCloud. Rytm nie może zmieniać jej głośności – " +
                "podczas komunikatów prosi system o przyciszenie.",
            volumeControllable = false,
        )
    }

    override fun play() = Unit
    override fun pause() = Unit
    override fun setVolume(volume: Float) = Unit
    override fun stop() {
        _state.value = MusicState(playback = MusicPlayback.EXTERNAL_APP, status = MusicStatus.NONE)
    }

    companion object {
        const val SOUNDCLOUD_PACKAGE = "com.soundcloud.android"

        /** Otwiera utwór w aplikacji SoundCloud (lub w przeglądarce, gdy aplikacja nie jest zainstalowana). */
        fun open(context: Context, url: String): Boolean {
            val uri = Uri.parse(url)
            val inApp = Intent(Intent.ACTION_VIEW, uri).setPackage(SOUNDCLOUD_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(inApp) }.isSuccess) return true
            return runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }

        fun isAppInstalled(context: Context): Boolean = runCatching {
            context.packageManager.getPackageInfo(SOUNDCLOUD_PACKAGE, 0)
        }.isSuccess
    }
}
