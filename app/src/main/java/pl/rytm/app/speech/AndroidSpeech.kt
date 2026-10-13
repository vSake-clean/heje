package pl.rytm.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.domain.session.SpeechListener
import pl.rytm.app.domain.session.SpeechOutput
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class VoiceInfo(
    val name: String,
    val label: String,
    val offline: Boolean,
    val quality: Int,
)

data class SpeechConfig(
    val voiceName: String? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    /** Głośność komunikatów 0..1 (względem głośności strumienia). */
    val volume: Float = 1f,
    /** true – strumień budzika (słychać nawet przy wyciszonych multimediach). */
    val alarmStream: Boolean = false,
)

/**
 * Polska synteza mowy przez systemowy Android TextToSpeech (działa offline, jeśli zainstalowany jest
 * polski głos – np. „Usługi mowy Google”). Nie wymaga żadnych kluczy ani kont.
 */
class AndroidSpeech(context: Context, initial: SpeechConfig = SpeechConfig()) : SpeechOutput {

    private val appContext = context.applicationContext
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var config = initial

    private val _availability = MutableStateFlow(SpeechAvailability.INITIALIZING)
    val availabilityFlow: StateFlow<SpeechAvailability> = _availability.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()

    /** Nazwa silnika (np. com.google.android.tts) – do wyświetlenia w ustawieniach. */
    var engineName: String? = null
        private set

    override val availability: SpeechAvailability get() = _availability.value
    override var listener: SpeechListener? = null

    private val tts: TextToSpeech = TextToSpeech(appContext) { status -> onInit(status) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed: $status")
            _availability.value = SpeechAvailability.UNAVAILABLE
            return
        }
        engineName = runCatching { tts.defaultEngine }.getOrNull()
        val lang = tts.setLanguage(POLISH)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Polish not available: $lang")
            _availability.value = SpeechAvailability.UNAVAILABLE
            refreshVoices()
            return
        }
        tts.setOnUtteranceProgressListener(progressListener)
        refreshVoices()
        applyConfig()
        _availability.value = SpeechAvailability.READY
    }

    fun refreshVoices() {
        val list = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == POLISH.language }
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))
            .map { v ->
                VoiceInfo(
                    name = v.name,
                    label = buildString {
                        append(v.name.substringAfterLast('#').substringAfter("pl-pl-x-").ifBlank { v.name })
                        append(if (v.isNetworkConnectionRequired) " · online" else " · offline")
                    },
                    offline = !v.isNetworkConnectionRequired,
                    quality = v.quality,
                )
            }
        _voices.value = list
    }

    fun updateConfig(newConfig: SpeechConfig) {
        config = newConfig
        if (availability == SpeechAvailability.READY) applyConfig()
    }

    private fun applyConfig() {
        val c = config
        tts.setSpeechRate(c.rate.coerceIn(0.5f, 2f))
        tts.setPitch(c.pitch.coerceIn(0.5f, 2f))
        val usage = if (c.alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val all = runCatching { tts.voices }.getOrNull().orEmpty()
        val chosen = c.voiceName?.let { name -> all.firstOrNull { it.name == name } }
            ?: all.filter {
                it.locale.language == POLISH.language && !it.isNetworkConnectionRequired &&
                    !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }.maxByOrNull { it.quality }
        if (chosen != null) runCatching { tts.voice = chosen }
    }

    override fun speak(id: String, text: String, flush: Boolean) {
        if (availability != SpeechAvailability.READY) {
            listener?.onError(id)
            return
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.volume.coerceIn(0f, 1f)) }
        val result = tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id)
        if (result != TextToSpeech.SUCCESS) listener?.onError(id)
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    /**
     * Mierzy rzeczywistą długość wypowiedzi wybranym głosem: syntezuje ją do pliku WAV
     * (bez odtwarzania) i odczytuje długość z nagłówka. Null, gdy pomiar się nie powiódł.
     */
    suspend fun measureDurationMs(text: String): Long? {
        if (availability != SpeechAvailability.READY || text.isBlank()) return null
        val id = "measure-${System.nanoTime()}"
        val file = File(appContext.cacheDir, "$id.wav")
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val params = Bundle()
        val result = tts.synthesizeToFile(text, params, file, id)
        if (result != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return null
        }
        val ok = withTimeoutOrNull(15_000) { done.await() } ?: false
        pending.remove(id)
        return try {
            if (ok && file.exists()) WavDuration.parseMs(file.readBytes()) else null
        } finally {
            file.delete()
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            if (utteranceId.startsWith("measure-")) return
            listener?.onStart(utteranceId)
        }

        override fun onDone(utteranceId: String) {
            pending[utteranceId]?.let { it.complete(true); return }
            listener?.onDone(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            pending[utteranceId]?.let { it.complete(false); return }
            listener?.onError(utteranceId)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            pending[utteranceId]?.let { it.complete(false); return }
            listener?.onError(utteranceId)
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            pending[utteranceId]?.let { it.complete(false); return }
            listener?.onDone(utteranceId)
        }
    }

    companion object {
        private const val TAG = "AndroidSpeech"
        val POLISH: Locale = Locale.forLanguageTag("pl-PL")
    }
}

/** Odczyt długości pliku WAV (PCM) z nagłówka RIFF. */
object WavDuration {
    fun parseMs(bytes: ByteArray): Long? {
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = le32(bytes, offset + 4)
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    channels = le16(bytes, body + 2)
                    sampleRate = le32(bytes, body + 4)
                    bitsPerSample = le16(bytes, body + 14)
                }
                "data" -> {
                    if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) return null
                    // Silniki zapisujące strumieniowo mogą zostawić rozmiar 0 lub -1 – wtedy liczymy z długości pliku.
                    val dataSize = if (size <= 0 || body + size > bytes.size) bytes.size - body else size
                    val bytesPerSecond = sampleRate.toLong() * channels * (bitsPerSample / 8)
                    if (bytesPerSecond <= 0) return null
                    return dataSize * 1000L / bytesPerSecond
                }
            }
            if (size < 0) return null
            offset = body + size + (size and 1)
        }
        return null
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
