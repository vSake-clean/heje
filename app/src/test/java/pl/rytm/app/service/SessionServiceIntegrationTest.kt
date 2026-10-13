package pl.rytm.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import pl.rytm.app.RytmApp
import pl.rytm.app.domain.session.Phase
import pl.rytm.app.routineOf
import pl.rytm.app.speech.AndroidSpeech
import java.time.Duration

/**
 * Test integracyjny usługi pierwszoplanowej na Robolectric: start sesji z bazy, powiadomienie typu
 * mediaPlayback, synteza mowy o czasie, sterowanie oficjalnym odtwarzaczem SoundCloud (WebView),
 * pauza i sprzątanie po zakończeniu. Zegar Androida (SystemClock) jest kontrolowany przez test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = RytmApp::class, sdk = [35])
class SessionServiceIntegrationTest {

    private val app = ApplicationProvider.getApplicationContext<RytmApp>()
    private val mainLooper = shadowOf(Looper.getMainLooper())

    @After
    fun cleanUp() {
        SessionHub.controller?.stop()
        repeat(30) {
            mainLooper.idle()
            Thread.sleep(100)
        }
    }

    @org.junit.Before
    fun resetSettings() {
        // DataStore jest współdzielony w procesie testów – zaczynamy od ustawień domyślnych (przyciszenie 25%).
        runBlocking { app.container.settings.update { pl.rytm.app.data.settings.AppSettings(onboardingDone = true) } }
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            mainLooper.idle()
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Warunek nie został spełniony w czasie $timeoutMs ms")
    }

    /** Przesuwa zegar systemowy Androida i daje wątkowi silnika czas na reakcję. */
    private fun advance(ms: Long) {
        mainLooper.idleFor(Duration.ofMillis(ms))
        Thread.sleep(400)
        mainLooper.idle()
    }

    private fun evaluatedScripts(view: WebView): String = shadowOf(view).lastEvaluatedJavascript.orEmpty()

    @Test
    fun `foreground session speaks on schedule and drives the official SoundCloud player`() {
        ShadowTextToSpeech.addLanguageAvailability(AndroidSpeech.POLISH)
        val id = runBlocking { app.container.routines.save(routineOf().copy(id = 0, name = "Poranek")) }

        val intent = Intent(app, RoutineSessionService::class.java)
            .setAction(RoutineSessionService.ACTION_START)
            .putExtra(RoutineSessionService.EXTRA_ROUTINE_ID, id)
        val service = Robolectric.buildService(RoutineSessionService::class.java, intent).create().startCommand(0, 1).get()

        // 1. Usługa pierwszoplanowa typu „mediaPlayback” z powiadomieniem.
        val shadowService = shadowOf(service)
        assertNotNull(shadowService.lastForegroundNotification)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, service.foregroundServiceType)

        // 2. Sesja startuje i publikuje stan dla interfejsu.
        waitUntil { SessionHub.live.value != null }
        val live = SessionHub.live.value!!
        assertEquals("Poranek", live.plan.routineName)
        assertEquals(Phase.INTRO, live.status.phase)

        // 3. Silnik mowy gotowy (polski).
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)

        // 4. Oficjalny odtwarzacz SoundCloud: strona odtwarzacza i polecenie load(...) przez Widget API.
        val player = SessionHub.player.value!!
        val container = FrameLayout(app)
        player.attachTo(container)
        val webView = container.getChildAt(0) as WebView
        assertTrue(shadowOf(webView).lastLoadDataWithBaseURL.baseUrl.startsWith("https://appassets.androidplatform.net/"))
        val bridge = shadowOf(webView).getJavascriptInterface("RytmBridge")
        fun callBridge(name: String, data: String = "") =
            bridge.javaClass.getMethod(name, String::class.java).apply { isAccessible = true }.invoke(bridge, data)
        callBridge("onPageLoaded")
        mainLooper.idle()
        val loadScript = evaluatedScripts(webView)
        assertTrue(loadScript, loadScript.startsWith("rytm.load(\"https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fforss%2Fflickermood"))
        callBridge("onReady")
        callBridge("onPlay")
        mainLooper.idle()

        // 5. Pierwsze polecenie dokładnie po 10 s (zegar systemowy przesuwany przez test).
        advance(9_000)
        assertNull(shadowOf(tts).lastSpokenText)
        advance(1_000)
        waitUntil { shadowOf(tts).lastSpokenText != null }
        assertEquals("Wstań z łóżka.", shadowOf(tts).lastSpokenText)

        // 6. Muzyka delikatnie przyciszona (75%) przez setVolume widżetu, bez zatrzymywania.
        advance(600)
        val seen = mutableListOf<String>()
        runCatching {
            waitUntil {
                val js = evaluatedScripts(webView)
                if (seen.lastOrNull() != js) seen += js
                js.startsWith("rytm.setVolume(75.0)")
            }
        }.onFailure { throw AssertionError("Brak setVolume(75.0); widziane skrypty: $seen; live=${SessionHub.live.value?.status}") }

        // 7. Po 15 s zadania – kolejne polecenie, bez czekania na potwierdzenie.
        advance(15_000)
        waitUntil { shadowOf(tts).lastSpokenText == "Podejdź do biurka." }

        // 8. Pauza z interfejsu: muzyka wstrzymana, stan i powiadomienie zaktualizowane.
        SessionHub.controller!!.pause()
        waitUntil { evaluatedScripts(webView) == "rytm.pause()" }
        waitUntil { SessionHub.live.value?.status?.paused == true }
        val nm = app.getSystemService(NotificationManager::class.java)
        waitUntil {
            shadowOf(nm).allNotifications.any {
                it.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.startsWith("Wstrzymano") == true
            }
        }
        assertTrue(app.container.sessionStore.hasSession())

        // 9. Zakończenie: usługa się zatrzymuje, stan sesji jest czyszczony.
        SessionHub.controller!!.stop()
        waitUntil(15_000) { SessionHub.controller == null }
        assertTrue(shadowService.isStoppedBySelf)
        assertNull(SessionHub.live.value)
        assertTrue(!app.container.sessionStore.hasSession())
    }

    @Test
    fun `session is recovered after the process was killed and continues on the original schedule`() {
        ShadowTextToSpeech.addLanguageAvailability(AndroidSpeech.POLISH)
        val routine = routineOf().copy(id = 0, name = "Po restarcie")
        val id = runBlocking { app.container.routines.save(routine) }
        // Sesja zapisana na dysku przez poprzedni (zabity) proces.
        val clock = AndroidSessionClock(app)
        val saved = pl.rytm.app.domain.session.SessionEngine.start(
            "killed", pl.rytm.app.domain.session.SessionPlan.from(routine.copy(id = id)), clock.now(),
        ).copy(firedCues = setOf("start:0", "start:1"))
        app.container.sessionStore.save(saved, sync = true)
        // Proces nie żył przez ~60 s.
        mainLooper.idleFor(Duration.ofSeconds(60))

        // START_STICKY: system uruchamia usługę ponownie z pustą intencją.
        Robolectric.buildService(RoutineSessionService::class.java, null).create().startCommand(0, 1)
        waitUntil { SessionHub.live.value != null }
        val live = SessionHub.live.value!!
        assertEquals(2, live.status.taskIndex) // 40–100 s: „Przygotuj ubrania”
        assertTrue(live.notice!!.startsWith("Sesja była przerwana"))

        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        advance(200)
        waitUntil { shadowOf(tts).lastSpokenText != null }
        assertEquals("Teraz: Przygotuj ubrania.", shadowOf(tts).lastSpokenText)
    }
}
