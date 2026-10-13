package pl.rytm.app.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.rytm.app.container
import pl.rytm.app.data.settings.AppSettings
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.schedule.RoutinePlanner
import pl.rytm.app.domain.schedule.ScheduleEditing
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.schedule.ScheduleValidator
import pl.rytm.app.domain.schedule.Severity
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.speech.AnnouncementTexts
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.domain.speech.MeasuredOrHeuristicEstimator
import pl.rytm.app.domain.speech.SpeechDurationEstimator
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.speech.AndroidSpeech
import pl.rytm.app.speech.SpeechConfig

/** Edytowane zadanie: index == null → nowe zadanie (dodawane na końcu). */
data class TaskDraft(
    val index: Int?,
    val task: RoutineTask,
    /** Najwcześniejszy dozwolony start (koniec poprzedniego zadania). */
    val earliestStartSec: Int,
)

data class EditorState(
    val routine: Routine = Routine(name = ""),
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val dirty: Boolean = false,
    val issues: List<ScheduleIssue> = emptyList(),
    val measured: Map<String, Long> = emptyMap(),
    val measuring: Boolean = false,
    val proposal: RoutinePlanner.Result? = null,
    val taskDraft: TaskDraft? = null,
    val message: String? = null,
    val saved: Boolean = false,
    val speech: SpeechAvailability = SpeechAvailability.INITIALIZING,
) {
    val totalSec: Int get() = TimelineBuilder.totalSec(routine.tasks)
    val blockingErrors: List<ScheduleIssue>
        get() = issues.filter { it.severity == Severity.ERROR && it !is ScheduleIssue.ExceedsWindow }
}

class EditorViewModel(app: Application, private val routineId: Long) : AndroidViewModel(app) {
    private val c = app.container
    private var settings = AppSettings()
    private var preview: AndroidSpeech? = null

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings = c.settings.current()
            val existing = if (routineId > 0) c.routines.getRoutine(routineId) else null
            val routine = existing ?: Routine(
                name = "",
                mode = settings.defaultMode,
                flexibleTimeoutSec = settings.defaultFlexTimeoutSec,
                interruptionBehavior = settings.defaultInterruption,
                musicPlayback = settings.defaultMusicPlayback,
                tasks = listOf(RoutineTask(title = "Pierwsze zadanie", spokenText = "Zaczynamy.", durationSec = 30, gapBeforeSec = 5)),
            )
            _state.value = EditorState(routine = routine, loaded = true, isNew = existing == null)
            revalidate()
        }
    }

    private fun estimator(): SpeechDurationEstimator =
        MeasuredOrHeuristicEstimator(_state.value.measured, HeuristicSpeechEstimator(settings.speechRate))

    private fun revalidate() {
        val s = _state.value
        val r = s.routine
        val issues = ScheduleValidator.validate(r.tasks, r.availableWindowSec, r.announcements, estimator())
        _state.value = s.copy(issues = issues)
    }

    private fun edit(f: (Routine) -> Routine) {
        _state.update { it.copy(routine = f(it.routine), dirty = true, saved = false) }
        revalidate()
    }

    fun estimateMs(text: String): Long = estimator().estimateMs(text)

    // ---------------------------------------------------------------- pola rutyny

    fun setName(v: String) = edit { it.copy(name = v) }
    fun setGoal(v: String) = edit { it.copy(goal = v) }
    fun setMode(v: ScheduleMode) = edit { it.copy(mode = v) }
    fun setFlexTimeout(sec: Int) = edit { it.copy(flexibleTimeoutSec = sec.coerceIn(0, 3600)) }
    fun setInterruption(v: InterruptionBehavior) = edit { it.copy(interruptionBehavior = v) }
    fun setWakeTime(minutes: Int?) = edit { it.copy(wakeTimeMinutes = minutes, alarmEnabled = it.alarmEnabled && minutes != null) }
    fun setLeaveTime(minutes: Int?) = edit { it.copy(leaveTimeMinutes = minutes) }
    fun setTargetDuration(sec: Int?) = edit { it.copy(targetDurationSec = sec?.takeIf { s -> s > 0 }) }
    fun setAlarmEnabled(v: Boolean) = edit { it.copy(alarmEnabled = v && it.wakeTimeMinutes != null) }
    fun toggleDay(bit: Int) = edit { it.copy(alarmDays = it.alarmDays xor (1 shl bit)) }
    fun setMusic(selection: MusicSelection?) = edit {
        it.copy(
            music = selection,
            musicPlayback = if (selection != null && it.musicPlayback == MusicPlayback.NONE) MusicPlayback.EMBEDDED else it.musicPlayback,
        )
    }
    fun setMusicPlayback(v: MusicPlayback) = edit { it.copy(musicPlayback = v) }
    fun setMusicVolume(v: Int) = edit { it.copy(musicVolume = v.coerceIn(0, 100)) }
    fun setMusicLoop(v: Boolean) = edit { it.copy(musicLoop = v) }
    fun setAnnouncements(f: (AnnouncementConfig) -> AnnouncementConfig) = edit { it.copy(announcements = f(it.announcements)) }

    // ---------------------------------------------------------------- zadania

    fun openNewTask() {
        val tasks = _state.value.routine.tasks
        val end = TimelineBuilder.totalSec(tasks)
        _state.update {
            it.copy(
                taskDraft = TaskDraft(
                    index = null,
                    task = RoutineTask(title = "", spokenText = "", durationSec = 30),
                    earliestStartSec = end,
                ),
            )
        }
    }

    fun openTask(index: Int) {
        val tasks = _state.value.routine.tasks
        val earliest = if (index == 0) 0 else TimelineBuilder.endSecOf(tasks, index - 1)
        _state.update { it.copy(taskDraft = TaskDraft(index, tasks[index], earliest)) }
    }

    fun closeTaskEditor() = _state.update { it.copy(taskDraft = null) }

    fun saveTask(draft: TaskDraft) {
        val task = draft.task.copy(
            title = draft.task.title.trim().ifBlank { draft.task.spokenText.trim().take(40).ifBlank { "Zadanie" } },
        )
        edit { r ->
            val tasks = if (draft.index == null) r.tasks + task else r.tasks.mapIndexed { i, t -> if (i == draft.index) task else t }
            r.copy(tasks = tasks)
        }
        _state.update { it.copy(taskDraft = null) }
    }

    fun duplicateTask(index: Int) = edit { it.copy(tasks = ScheduleEditing.duplicate(it.tasks, index)) }
    fun deleteTask(index: Int) = edit { it.copy(tasks = ScheduleEditing.remove(it.tasks, index)) }
    fun moveTask(index: Int, delta: Int) = edit { it.copy(tasks = ScheduleEditing.move(it.tasks, index, index + delta)) }
    fun shiftTask(index: Int, deltaSec: Int) = edit { it.copy(tasks = ScheduleEditing.shift(it.tasks, index, deltaSec)) }
    fun removeGaps() = edit { it.copy(tasks = ScheduleEditing.removeGaps(it.tasks)) }

    // ---------------------------------------------------------------- dopasowanie do okna czasowego

    fun proposeFit() {
        val r = _state.value.routine
        val available = r.availableWindowSec ?: return
        _state.update { it.copy(proposal = RoutinePlanner.fit(r.tasks, available)) }
    }

    fun applyProposal() {
        val p = _state.value.proposal as? RoutinePlanner.Result.Proposal ?: return
        edit { it.copy(tasks = p.tasks) }
        _state.update { it.copy(proposal = null, message = "Plan dopasowany do dostępnego czasu. Możesz go jeszcze zmienić ręcznie.") }
    }

    fun dismissProposal() = _state.update { it.copy(proposal = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- mowa: odsłuch i pomiar

    private fun speech(): AndroidSpeech {
        val existing = preview
        if (existing != null) return existing
        val s = AndroidSpeech(
            getApplication(),
            SpeechConfig(
                voiceName = settings.voiceName,
                rate = settings.speechRate,
                pitch = settings.speechPitch,
                volume = _state.value.routine.announcements.volume / 100f,
            ),
        )
        preview = s
        viewModelScope.launch { s.availabilityFlow.collect { a -> _state.update { it.copy(speech = a) } } }
        return s
    }

    fun warmUpSpeech() {
        speech()
    }

    fun previewSpeech(text: String) {
        val s = speech()
        s.updateConfig(
            SpeechConfig(
                voiceName = settings.voiceName,
                rate = settings.speechRate,
                pitch = settings.speechPitch,
                volume = _state.value.routine.announcements.volume / 100f,
            ),
        )
        if (s.availability == SpeechAvailability.READY) {
            s.speak("preview-${System.nanoTime()}", text, flush = true)
        } else if (s.availability == SpeechAvailability.UNAVAILABLE) {
            _state.update { it.copy(message = "Brak polskiego głosu w systemie. Zainstaluj go w Ustawieniach → Głos.") }
        }
    }

    /** Mierzy rzeczywistą długość komunikatów wybranym głosem (synteza do pliku, bez odtwarzania). */
    fun measureAll() {
        val s = speech()
        viewModelScope.launch {
            _state.update { it.copy(measuring = true) }
            var waited = 0
            while (s.availability == SpeechAvailability.INITIALIZING && waited < 50) {
                kotlinx.coroutines.delay(100)
                waited++
            }
            if (s.availability != SpeechAvailability.READY) {
                _state.update { it.copy(measuring = false, message = "Synteza mowy jest niedostępna – używam szacunków.") }
                return@launch
            }
            val r = _state.value.routine
            val texts = r.tasks.map { AnnouncementTexts.taskStart(it.announcementText, it.durationSec, r.announcements) } +
                listOfNotNull(
                    r.announcements.startMessage.takeIf { it.isNotBlank() },
                    r.announcements.endMessage.takeIf { it.isNotBlank() },
                    r.announcements.warningBeforeEndSec.takeIf { it > 0 }?.let { AnnouncementTexts.warning(it) },
                )
            val results = HashMap(_state.value.measured)
            for (t in texts.distinct()) {
                s.measureDurationMs(t)?.let { results[t.trim()] = it }
            }
            _state.update {
                it.copy(
                    measured = results,
                    measuring = false,
                    message = "Zmierzono ${results.size} komunikatów wybranym głosem.",
                )
            }
            revalidate()
        }
    }

    // ---------------------------------------------------------------- zapis

    fun save() {
        val s = _state.value
        val r = s.routine
        if (r.name.isBlank()) {
            _state.update { it.copy(message = "Podaj nazwę rutyny.") }
            return
        }
        if (s.blockingErrors.isNotEmpty()) {
            _state.update { it.copy(message = "Popraw błędy w harmonogramie: " + s.blockingErrors.joinToString("; ") { e -> describeIssue(e, r.tasks) }) }
            return
        }
        viewModelScope.launch {
            val id = c.routines.save(r)
            val saved = c.routines.getRoutine(id)
            val alarmAt = saved?.let { c.alarms.schedule(it) }
            val alarmInfo = if (saved?.alarmEnabled == true && alarmAt == null && !c.alarms.canScheduleExact()) {
                " Budzik wymaga zgody na dokładne alarmy (Ustawienia → Działanie w tle)."
            } else {
                ""
            }
            _state.update {
                it.copy(
                    routine = saved ?: r,
                    dirty = false,
                    isNew = false,
                    saved = true,
                    message = if (alarmInfo.isNotEmpty()) "Zapisano.$alarmInfo" else null,
                )
            }
        }
    }

    override fun onCleared() {
        preview?.shutdown()
        super.onCleared()
    }
}
