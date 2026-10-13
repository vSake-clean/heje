package pl.rytm.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/**
 * Wykrywa rozmowy telefoniczne i komunikatory głosowe na podstawie trybu audio
 * (nie wymaga uprawnienia do odczytu stanu telefonu).
 */
class CallMonitor(context: Context, private val onChange: (inCall: Boolean) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var last: Boolean? = null
    private var running = false
    private var modeListener: Any? = null

    private val poll = object : Runnable {
        override fun run() {
            check()
            if (running) main.postDelayed(this, 1_000)
        }
    }

    fun start() {
        if (running) return
        running = true
        val registered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
            val listener = AudioManager.OnModeChangedListener { check() }
            audio.addOnModeChangedListener(Executor { main.post(it) }, listener)
            modeListener = listener
        }.isSuccess
        if (registered) check() else main.post(poll) // starsze Androidy lub błąd rejestracji – odpytywanie co 1 s
    }

    fun stop() {
        running = false
        main.removeCallbacks(poll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modeListener as? AudioManager.OnModeChangedListener)?.let { runCatching { audio.removeOnModeChangedListener(it) } }
        }
        modeListener = null
    }

    private fun check() {
        val inCall = isCallMode(audio.mode)
        if (inCall != last) {
            last = inCall
            onChange(inCall)
        }
    }

    companion object {
        fun isCallMode(mode: Int): Boolean = mode != AudioManager.MODE_NORMAL && mode != AudioManager.MODE_INVALID &&
            mode != AudioManager.MODE_CURRENT
    }
}

/** Informacje o wyjściu audio. */
object AudioRoutes {
    /** Czy podłączone są słuchawki (przewodowe, USB lub Bluetooth). */
    fun headphonesConnected(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val types = buildSet {
            add(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        return runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types } }.getOrDefault(false)
    }
}

/**
 * Tryb zewnętrznej aplikacji SoundCloud: nie mamy dostępu do jej głośności, więc na czas komunikatu
 * prosimy system o przyciszenie innych aplikacji (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK). Poziom
 * przyciszenia ustala Android. Fokus zwalniamy z opóźnieniem, żeby bliskie komunikaty nie powodowały skoków.
 */
class SystemDucker(context: Context, private val holdMs: Long) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null
    private val abandonRunnable = Runnable { abandonNow() }

    fun speaking(active: Boolean) {
        main.post {
            if (active) {
                main.removeCallbacks(abandonRunnable)
                if (request == null) {
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        .setOnAudioFocusChangeListener({ }, main)
                        .build()
                    if (audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request = req
                }
            } else {
                main.removeCallbacks(abandonRunnable)
                main.postDelayed(abandonRunnable, holdMs)
            }
        }
    }

    fun release() {
        main.removeCallbacks(abandonRunnable)
        main.post { abandonNow() }
    }

    private fun abandonNow() {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
    }
}
