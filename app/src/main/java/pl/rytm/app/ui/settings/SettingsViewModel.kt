package pl.rytm.app.ui.settings

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.rytm.app.container
import pl.rytm.app.data.settings.AppSettings
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.speech.AndroidSpeech
import pl.rytm.app.speech.SpeechConfig
import pl.rytm.app.speech.VoiceInfo
import pl.rytm.app.ui.SystemChecks
import pl.rytm.app.ui.SystemStatus

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val settings: StateFlow<AppSettings?> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _system = MutableStateFlow(SystemChecks.status(app))
    val system: StateFlow<SystemStatus> = _system.asStateFlow()

    private var speech: AndroidSpeech? = null
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()
    private val _speechState = MutableStateFlow(SpeechAvailability.INITIALIZING)
    val speechState: StateFlow<SpeechAvailability> = _speechState.asStateFlow()
    private val _engine = MutableStateFlow<String?>(null)
    val engine: StateFlow<String?> = _engine.asStateFlow()

    fun refreshSystem() {
        _system.value = SystemChecks.status(getApplication())
        speech?.refreshVoices()
    }

    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(f) }

    /** Uruchamia silnik mowy (lista głosów, test). */
    fun initSpeech() {
        if (speech != null) return
        val s = AndroidSpeech(getApplication(), configFrom(settings.value ?: AppSettings()))
        speech = s
        viewModelScope.launch {
            s.availabilityFlow.collect {
                _speechState.value = it
                _engine.value = s.engineName
            }
        }
        viewModelScope.launch { s.voices.collect { _voices.value = it } }
    }

    /** Ponowne sprawdzenie po instalacji głosu. */
    fun restartSpeech() {
        speech?.shutdown()
        speech = null
        _speechState.value = SpeechAvailability.INITIALIZING
        initSpeech()
    }

    fun testVoice(text: String = "Wstań z łóżka. Masz piętnaście sekund.") {
        val s = speech ?: run {
            initSpeech()
            speech!!
        }
        s.updateConfig(configFrom(settings.value ?: AppSettings()))
        if (s.availability == SpeechAvailability.READY) s.speak("test-${System.nanoTime()}", text, flush = true)
    }

    private fun configFrom(a: AppSettings) = SpeechConfig(
        voiceName = a.voiceName,
        rate = a.speechRate,
        pitch = a.speechPitch,
        volume = 1f,
        alarmStream = false,
    )

    fun completeOnboarding() = update { it.copy(onboardingDone = true) }

    /** Wylogowanie z SoundCloud w przeglądarce wbudowanej (usuwa ciasteczka i dane stron). */
    fun clearSoundCloudData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    override fun onCleared() {
        speech?.shutdown()
        super.onCleared()
    }
}
