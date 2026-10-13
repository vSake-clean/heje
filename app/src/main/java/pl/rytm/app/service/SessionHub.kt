package pl.rytm.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.rytm.app.domain.session.LiveSession
import pl.rytm.app.music.SoundCloudWidgetPlayer

/** Polecenia do trwającej sesji (implementuje usługa pierwszoplanowa). */
interface SessionController {
    fun pause()
    fun resume()
    fun confirm()
    fun stop()
    fun retryMusic()
}

data class EndedSession(val routineName: String, val stoppedByUser: Boolean, val durationMs: Long)

/**
 * Most między usługą (która prowadzi harmonogram) a interfejsem (który go tylko wyświetla).
 * Interfejs nigdy nie odmierza czasu – czyta stan publikowany przez usługę.
 */
object SessionHub {
    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private val _ended = MutableStateFlow<EndedSession?>(null)
    val ended: StateFlow<EndedSession?> = _ended.asStateFlow()

    /** Komunikat o błędzie startu (np. brak zadań). */
    private val _startError = MutableStateFlow<String?>(null)
    val startError: StateFlow<String?> = _startError.asStateFlow()

    @Volatile
    var controller: SessionController? = null
        internal set

    private val _player = MutableStateFlow<SoundCloudWidgetPlayer?>(null)
    val player: StateFlow<SoundCloudWidgetPlayer?> = _player.asStateFlow()

    /** Sesja uruchomiona budzikiem – ekran sesji pokazuje się nad ekranem blokady i włącza ekran. */
    private val _fromAlarm = MutableStateFlow(false)
    val fromAlarm: StateFlow<Boolean> = _fromAlarm.asStateFlow()

    internal fun publish(live: LiveSession?) {
        _live.value = live
    }

    internal fun setPlayer(player: SoundCloudWidgetPlayer?) {
        _player.value = player
    }

    internal fun setFromAlarm(value: Boolean) {
        _fromAlarm.value = value
    }

    internal fun markEnded(summary: EndedSession?) {
        _ended.value = summary
    }

    fun clearEnded() {
        _ended.value = null
    }

    internal fun reportStartError(message: String?) {
        _startError.value = message
    }

    fun clearStartError() {
        _startError.value = null
    }

    val isActive: Boolean get() = controller != null
}
