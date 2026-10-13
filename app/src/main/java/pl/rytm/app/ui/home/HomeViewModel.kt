package pl.rytm.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.rytm.app.container
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.schedule.RoutinePlanner
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.session.SessionPlan
import pl.rytm.app.music.ExternalSoundCloudOutput
import pl.rytm.app.music.SoundCloudResolver
import pl.rytm.app.service.RoutineSessionService
import pl.rytm.app.service.SessionHub
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

sealed interface HomeDialog {
    /** Rutyna nie zdąży przed godziną wyjścia – propozycja dopasowania. */
    data class LeaveConflict(
        val routine: Routine,
        val availableSec: Int,
        val totalSec: Int,
        val result: RoutinePlanner.Result,
    ) : HomeDialog

    data class ConfirmDelete(val routine: Routine) : HomeDialog

    /** Udostępniony link SoundCloud – wybierz rutynę, do której go dodać. */
    data class ShareTarget(val selection: MusicSelection, val warning: String? = null) : HomeDialog
    data class Message(val title: String, val text: String) : HomeDialog
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val routines: StateFlow<List<Routine>> =
        c.routines.observeRoutines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val live = SessionHub.live
    val ended = SessionHub.ended
    val startError = SessionHub.startError

    private val _dialog = MutableStateFlow<HomeDialog?>(null)
    val dialog: StateFlow<HomeDialog?> = _dialog.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun dismissDialog() {
        _dialog.value = null
    }

    fun clearEnded() = SessionHub.clearEnded()
    fun clearStartError() = SessionHub.clearStartError()

    /**
     * Start rutyny. Jeśli rutyna ma godzinę wyjścia i nie zdąży – pokazuje konflikt z propozycją
     * dopasowania (zadania obowiązkowe nigdy nie są zmieniane bez zgody).
     */
    fun requestStart(routine: Routine, onStarted: () -> Unit) {
        if (routine.tasks.isEmpty()) {
            _dialog.value = HomeDialog.Message("Brak zadań", "Dodaj przynajmniej jedno zadanie, aby rozpocząć rutynę.")
            return
        }
        if (SessionHub.isActive) {
            onStarted()
            return
        }
        val leave = routine.leaveTimeMinutes
        if (leave != null) {
            val available = secondsUntil(leave)
            val total = TimelineBuilder.totalSec(routine.tasks)
            if (available != null && total > available) {
                _dialog.value = HomeDialog.LeaveConflict(routine, available, total, RoutinePlanner.fit(routine.tasks, available))
                return
            }
        }
        start(routine, null)
        onStarted()
    }

    fun startWithTasks(routine: Routine, tasks: List<RoutineTask>, onStarted: () -> Unit) {
        _dialog.value = null
        viewModelScope.launch {
            val s = c.settings.current()
            val plan = SessionPlan.from(routine, tasks, s.announceTiming, s.ttsEnabled)
            start(routine, plan)
            onStarted()
        }
    }

    fun startAnyway(routine: Routine, onStarted: () -> Unit) {
        _dialog.value = null
        start(routine, null)
        onStarted()
    }

    private fun start(routine: Routine, plan: SessionPlan?) {
        val ctx = getApplication<Application>()
        RoutineSessionService.start(ctx, routine.id, plan)
        // Tryb zewnętrzny: otwórz utwór w aplikacji SoundCloud (z poziomu widocznej aktywności).
        val music = routine.music
        if (routine.effectiveMusicPlayback == MusicPlayback.EXTERNAL_APP && music != null) {
            ExternalSoundCloudOutput.open(ctx, music.url)
        }
    }

    fun duplicate(routine: Routine) = viewModelScope.launch { c.routines.duplicate(routine.id) }

    fun askDelete(routine: Routine) {
        _dialog.value = HomeDialog.ConfirmDelete(routine)
    }

    fun delete(routine: Routine) = viewModelScope.launch {
        _dialog.value = null
        c.alarms.cancel(routine.id)
        c.routines.delete(routine.id)
    }

    fun setAlarm(routine: Routine, enabled: Boolean) = viewModelScope.launch {
        if (enabled && routine.wakeTimeMinutes == null) {
            _dialog.value = HomeDialog.Message(
                "Ustaw godzinę pobudki",
                "Budzik uruchamia rutynę o godzinie pobudki. Ustaw ją w edytorze rutyny.",
            )
            return@launch
        }
        c.routines.setAlarmEnabled(routine.id, enabled)
        c.routines.getRoutine(routine.id)?.let { c.alarms.schedule(it) }
    }

    // ---------------------------------------------------------------- udostępnianie z SoundCloud

    fun handleSharedText(text: String) {
        viewModelScope.launch {
            _busy.value = true
            when (val r = SoundCloudResolver().resolve(text)) {
                is SoundCloudResolver.Result.Ok -> _dialog.value = HomeDialog.ShareTarget(r.selection, r.warning)
                is SoundCloudResolver.Result.Error -> _dialog.value = HomeDialog.Message("Nie można dodać muzyki", r.message)
            }
            _busy.value = false
        }
    }

    fun attachMusic(routine: Routine, selection: MusicSelection) = viewModelScope.launch {
        _dialog.value = null
        val playback = if (routine.musicPlayback == MusicPlayback.NONE) MusicPlayback.EMBEDDED else routine.musicPlayback
        c.routines.setMusic(routine.id, selection, playback)
        _dialog.value = HomeDialog.Message("Dodano muzykę", "„${selection.title}” będzie grać w rutynie „${routine.name}”.")
    }

    companion object {
        /**
         * Sekundy do najbliższej podanej godziny (dziś lub jutro). Null, gdy to dalej niż 16 h –
         * wtedy rutyna raczej nie jest uruchamiana „na to wyjście”.
         */
        fun secondsUntil(minutesOfDay: Int, now: LocalDateTime = LocalDateTime.now()): Int? {
            var target = now.toLocalDate().atTime(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val sec = ChronoUnit.SECONDS.between(now, target)
            return if (sec > 16 * 3600) null else sec.toInt()
        }
    }
}
