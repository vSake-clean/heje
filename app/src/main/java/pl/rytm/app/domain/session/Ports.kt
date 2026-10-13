package pl.rytm.app.domain.session

import kotlinx.coroutines.flow.StateFlow
import pl.rytm.app.domain.model.MusicPlayback

/** Źródło czasu – w aplikacji SystemClock.elapsedRealtime(), w testach zegar wirtualny. */
fun interface SessionClock {
    fun now(): ClockReading
}

enum class SpeechAvailability {
    INITIALIZING,
    READY,

    /** Brak polskiego głosu lub silnika mowy – komunikaty są pomijane, harmonogram działa dalej. */
    UNAVAILABLE,
    DISABLED,
}

interface SpeechListener {
    fun onStart(id: String)
    fun onDone(id: String)
    fun onError(id: String)
}

/** Synteza mowy. Implementacja musi wywoływać [SpeechListener] dla każdej wypowiedzi. */
interface SpeechOutput {
    val availability: SpeechAvailability
    var listener: SpeechListener?

    /** @param flush true – przerwij bieżącą wypowiedź (komunikaty nigdy się nie nakładają). */
    fun speak(id: String, text: String, flush: Boolean)
    fun stop()
}

enum class MusicStatus {
    NONE,
    LOADING,
    PLAYING,
    PAUSED,

    /** Brak internetu – odtwarzacz wznowi się po odzyskaniu połączenia. Harmonogram działa dalej. */
    WAITING_FOR_NETWORK,

    /** Inna aplikacja przejęła dźwięk (np. połączenie, inny odtwarzacz). */
    INTERRUPTED,
    ERROR,

    /** Muzyką steruje zewnętrzna aplikacja SoundCloud. */
    EXTERNAL,
}

data class MusicState(
    val playback: MusicPlayback = MusicPlayback.NONE,
    val status: MusicStatus = MusicStatus.NONE,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val permalinkUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val message: String? = null,
    /** Czy aplikacja bezpośrednio kontroluje głośność (przyciszanie). */
    val volumeControllable: Boolean = false,
)

/** Odtwarzacz muzyki. Błędy muzyki NIGDY nie wpływają na harmonogram. */
interface MusicOutput {
    val state: StateFlow<MusicState>
    fun prepare(plan: SessionPlan, autoPlay: Boolean)
    fun play()
    fun pause()

    /** Głośność odtwarzacza 0..100 (nie zmienia głośności systemowej). */
    fun setVolume(volume: Float)
    fun stop()
}

/** Trwały zapis migawki sesji. */
interface SessionPersistence {
    fun save(snapshot: SessionSnapshot, sync: Boolean)
    fun clear()
}

/** Efekty uboczne po stronie systemu (powiadomienia, wibracje, MediaSession). */
interface SessionEffects {
    fun onLiveUpdate(live: LiveSession) {}
    fun onTaskChanged(live: LiveSession) {}
    fun onEnded(live: LiveSession?, stoppedByUser: Boolean) {}

    /** Prośba o (nie)przyciszanie innej aplikacji przez system – tryb zewnętrznej aplikacji SoundCloud. */
    fun onSpeechAudioFocus(speaking: Boolean) {}
}

/** Stan sesji publikowany do interfejsu i powiadomień. */
data class LiveSession(
    val sessionId: String,
    val plan: SessionPlan,
    val status: SessionStatus,
    val music: MusicState,
    val speech: SpeechAvailability,
    val callActive: Boolean,
    val notice: String?,
    val ending: Boolean,
    val nowWall: Long,
    val nowElapsed: Long,
) {
    /** Przewidywana godzina zakończenia (zegar ścienny). */
    val etaWall: Long get() = nowWall + status.totalRemainingMs

    /** Godzina ścienna początku zadania [index] (wg bieżącego planu efektywnego). */
    fun wallTimeOf(positionMs: Long): Long = nowWall + (positionMs - status.positionMs)
}
