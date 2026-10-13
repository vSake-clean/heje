package pl.rytm.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.domain.session.SpeechListener
import pl.rytm.app.domain.session.SpeechOutput
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Komunikaty odtwarzane z nagrań przygotowanych przed sesją.
 *
 * Dlaczego: niektóre silniki mowy (zależnie od producenta telefonu) przy mówieniu przejmują
 * fokus audio i przez to WSTRZYMUJĄ muzykę zamiast pozwolić ją przyciszyć. Tutaj silnik tylko
 * syntezuje teksty do plików (bez odtwarzania), a Rytm odtwarza je sam przez MediaPlayer –
 * bez żadnej prośby o fokus audio. Muzyka gra dalej, przyciszana wyłącznie przez Rytm.
 * Dodatkowo polecenia startują natychmiast (synteza już jest zrobiona).
 *
 * Gdy nagrania jeszcze nie ma, komunikat jest wypowiadany bezpośrednio przez silnik (zapas).
 */
class ClipSpeechOutput(context: Context, private val engine: AndroidSpeech) : SpeechOutput {

    private val dir = File(context.applicationContext.cacheDir, "speech-clips")
    private val thread = HandlerThread("rytm-speech", Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val handler = Handler(thread.looper)
    private val clips = ConcurrentHashMap<String, File>()
    private var player: MediaPlayer? = null
    private var currentId: String? = null
    private var renderJob: Job? = null

    @Volatile
    private var released = false

    /** Liczba gotowych nagrań (diagnostyka/ekran). */
    val readyClips: Int get() = clips.size

    override val availability: SpeechAvailability get() = engine.availability

    override var listener: SpeechListener? = null

    /** Wypowiedź mówiona bezpośrednio przez silnik (gdy nagrania jeszcze nie ma). */
    @Volatile
    private var engineSpeakingId: String? = null

    init {
        engine.listener = object : SpeechListener {
            override fun onStart(id: String) {
                listener?.onStart(id)
            }

            override fun onDone(id: String) {
                if (engineSpeakingId == id) engineSpeakingId = null
                listener?.onDone(id)
            }

            override fun onError(id: String) {
                if (engineSpeakingId == id) engineSpeakingId = null
                listener?.onError(id)
            }
        }
    }

    /** Syntezuje w tle wszystkie teksty sesji (najpierw najbliższe). */
    fun prerender(texts: List<String>, scope: CoroutineScope) {
        renderJob?.cancel()
        renderJob = scope.launch(Dispatchers.IO) {
            var waited = 0
            while (engine.availability == SpeechAvailability.INITIALIZING && waited < 300 && isActive) {
                delay(100)
                waited++
            }
            if (engine.availability != SpeechAvailability.READY) return@launch
            dir.mkdirs()
            // Dwa przejścia: synteza może zostać przerwana, gdy silnik musi coś powiedzieć bezpośrednio.
            repeat(2) {
                for (raw in texts) {
                    if (!isActive || released) return@launch
                    val text = raw.trim()
                    if (text.isEmpty() || clips.containsKey(text)) continue
                    val file = File(dir, keyOf(text) + ".wav")
                    if (engine.synthesizeToFile(text, file)) clips[text] = file else file.delete()
                }
            }
        }
    }

    override fun speak(id: String, text: String, flush: Boolean) {
        handler.post {
            if (released) return@post
            if (flush) stopCurrent()
            val clip = clips[text.trim()]
            if (clip != null && clip.exists() && playClip(id, clip)) return@post
            engineSpeakingId = id
            engine.speak(id, text, flush = true)
        }
    }

    override fun stop() {
        handler.post { stopCurrent() }
    }

    fun release() {
        released = true
        renderJob?.cancel()
        handler.post {
            stopCurrent()
            dir.listFiles()?.forEach { it.delete() }
            thread.quitSafely()
        }
    }

    private fun playClip(id: String, file: File): Boolean {
        val cfg = engine.currentConfig
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (cfg.alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { finish(id, error = false) }
            mp.setOnErrorListener { _, _, _ ->
                finish(id, error = true)
                true
            }
            mp.prepare()
            val v = cfg.volume.coerceIn(0f, 1f)
            mp.setVolume(v, v)
            player = mp
            currentId = id
            mp.start()
            listener?.onStart(id)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się odtworzyć nagrania komunikatu – mówię bezpośrednio", e)
            runCatching { mp.release() }
            if (player === mp) player = null
            if (currentId == id) currentId = null
            false
        }
    }

    private fun finish(id: String, error: Boolean) {
        handler.post {
            if (currentId != id) return@post
            currentId = null
            releasePlayer()
            if (error) listener?.onError(id) else listener?.onDone(id)
        }
    }

    private fun stopCurrent() {
        val id = currentId
        if (id != null) {
            currentId = null
            releasePlayer()
            listener?.onDone(id)
        }
        // Silnik zatrzymujemy tylko, gdy sam mówi – stop() przerwałby też trwającą syntezę nagrań.
        if (engineSpeakingId != null) {
            engineSpeakingId = null
            engine.stop()
        }
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    companion object {
        private const val TAG = "ClipSpeech"

        fun keyOf(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
