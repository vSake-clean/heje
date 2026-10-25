package pl.rytm.app.ui.importer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.rytm.app.container
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.schedule.ScheduleValidator
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.domain.text.RoutineText
import pl.rytm.app.music.SoundCloudResolver

/** Tekst udostępniony do aplikacji (np. odpowiedź z ChatGPT) – czeka na ekran „Rutyna z tekstu”. */
object ImportInbox {
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    fun put(text: String) {
        _text.value = text
    }

    fun take(): String? = _text.value.also { _text.value = null }
}

data class ImportState(
    val text: String = "",
    val requirements: String = "",
    val result: RoutineText.Result? = null,
    /** Problemy harmonogramu (np. nie mieści się do wyjścia, za długie polecenie). */
    val scheduleIssues: List<ScheduleIssue> = emptyList(),
    val creating: Boolean = false,
    val message: String? = null,
)

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private var defaults = RoutineText.Defaults()

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    init {
        // Tekst udostępniony z innej aplikacji (także gdy ten ekran jest już otwarty).
        viewModelScope.launch {
            ImportInbox.text.collect { if (it != null) ImportInbox.take()?.let { t -> setText(t) } }
        }
        viewModelScope.launch {
            val s = c.settings.current()
            defaults = RoutineText.Defaults(
                mode = s.defaultMode,
                flexibleTimeoutSec = s.defaultFlexTimeoutSec,
                interruption = s.defaultInterruption,
                musicPlayback = if (s.defaultMusicPlayback == MusicPlayback.NONE) MusicPlayback.EMBEDDED else s.defaultMusicPlayback,
            )
            setText(_state.value.text)
        }
    }

    val prompt: String get() = RoutineText.aiPrompt(_state.value.requirements)

    fun setRequirements(v: String) = _state.update { it.copy(requirements = v) }

    fun setText(text: String) {
        val result = if (text.isBlank()) null else RoutineText.parse(text, defaults)
        val issues = result?.routine?.let { r ->
            ScheduleValidator.validate(r.tasks, r.availableWindowSec, r.announcements, HeuristicSpeechEstimator())
                .filter { it !is ScheduleIssue.NoTasks && it !is ScheduleIssue.ZeroDuration && it !is ScheduleIssue.EmptyTitle }
        }.orEmpty()
        _state.update { it.copy(text = text, result = result, scheduleIssues = issues) }
    }

    fun insertExample() = setText(RoutineText.EXAMPLE)

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Zapisuje rutynę (z muzyką, jeśli podano link SoundCloud) i zwraca jej id oraz ewentualną uwagę. */
    fun create(onCreated: (Long, String?) -> Unit) {
        val result = _state.value.result ?: return
        val parsed = result.routine ?: return
        if (!result.canCreate || _state.value.creating) return
        viewModelScope.launch {
            _state.update { it.copy(creating = true) }
            var routine = parsed
            var note: String? = null
            result.musicLink?.let { link ->
                when (val r = SoundCloudResolver().resolve(link)) {
                    is SoundCloudResolver.Result.Ok -> routine = routine.copy(music = r.selection)
                    is SoundCloudResolver.Result.Error -> note = "Muzyki nie dodano: ${r.message} Wybierz ją w edytorze."
                }
            }
            val id = c.routines.save(routine)
            c.routines.getRoutine(id)?.let { c.alarms.schedule(it) }
            _state.update { it.copy(creating = false) }
            onCreated(id, note)
        }
    }
}
