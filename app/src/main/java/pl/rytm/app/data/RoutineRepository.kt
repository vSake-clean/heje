package pl.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.rytm.app.data.db.RoutineDao
import pl.rytm.app.data.db.RoutineEntity
import pl.rytm.app.data.db.RoutineWithTasks
import pl.rytm.app.data.db.TaskEntity
import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode

/** Lokalne przechowywanie rutyn (Room). Żadnych danych nie wysyłamy na zewnątrz. */
class RoutineRepository(private val dao: RoutineDao, private val now: () -> Long = System::currentTimeMillis) {

    fun observeRoutines(): Flow<List<Routine>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRoutine(id: Long): Flow<Routine?> = dao.observe(id).map { it?.toDomain() }

    suspend fun getRoutine(id: Long): Routine? = dao.get(id)?.toDomain()

    suspend fun getAll(): List<Routine> = dao.getAll().map { it.toDomain() }

    suspend fun save(routine: Routine): Long {
        val existing = if (routine.id != 0L) dao.get(routine.id) else null
        val created = existing?.routine?.createdAt ?: now()
        return dao.upsert(routine.toEntity(created, now()), routine.tasks.map { it.toEntity() })
    }

    suspend fun duplicate(id: Long): Long? {
        val source = getRoutine(id) ?: return null
        val copy = source.copy(
            id = 0,
            name = "${source.name} (kopia)",
            alarmEnabled = false,
            lastUsedAt = null,
            tasks = source.tasks.map { it.copy(id = 0) },
        )
        return save(copy)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markUsed(id: Long) = dao.markUsed(id, now())

    suspend fun setAlarmEnabled(id: Long, enabled: Boolean) = dao.setAlarmEnabled(id, enabled, now())

    suspend fun setMusic(id: Long, music: MusicSelection?, playback: MusicPlayback) = dao.setMusic(
        id = id,
        url = music?.url,
        title = music?.title,
        artist = music?.artist,
        artwork = music?.artworkUrl,
        kind = music?.kind?.name,
        playback = playback.name,
        time = now(),
    )

    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        SampleRoutines.all().forEach { save(it) }
    }
}

internal fun RoutineWithTasks.toDomain(): Routine {
    val r = routine
    val music = r.musicUrl?.let {
        MusicSelection(
            url = it,
            title = r.musicTitle ?: it,
            artist = r.musicArtist.orEmpty(),
            artworkUrl = r.musicArtworkUrl,
            kind = enumOr(r.musicKind, MusicKind.TRACK),
        )
    }
    return Routine(
        id = r.id,
        name = r.name,
        goal = r.goal,
        tasks = tasks.sortedBy { it.position }.map {
            RoutineTask(
                id = it.id,
                title = it.title,
                spokenText = it.spokenText,
                durationSec = it.durationSec,
                gapBeforeSec = it.gapBeforeSec,
                mandatory = it.mandatory,
                minDurationSec = it.minDurationSec,
                notes = it.notes,
            )
        },
        mode = enumOr(r.mode, ScheduleMode.STRICT),
        flexibleTimeoutSec = r.flexibleTimeoutSec,
        interruptionBehavior = enumOr(r.interruptionBehavior, InterruptionBehavior.CONTINUE_SCHEDULE),
        targetDurationSec = r.targetDurationSec,
        wakeTimeMinutes = r.wakeTimeMinutes,
        leaveTimeMinutes = r.leaveTimeMinutes,
        alarmEnabled = r.alarmEnabled,
        alarmDays = r.alarmDays,
        music = music,
        musicPlayback = enumOr(r.musicPlayback, MusicPlayback.EMBEDDED),
        musicVolume = r.musicVolume,
        musicLoop = r.musicLoop,
        announcements = AnnouncementConfig(
            enabled = r.voiceEnabled,
            volume = r.voiceVolume,
            includeDuration = r.voiceIncludeDuration,
            warningBeforeEndSec = r.voiceWarningSec,
            startMessage = r.voiceStartMessage,
            endMessage = r.voiceEndMessage,
        ),
        lastUsedAt = r.lastUsedAt,
    )
}

internal fun Routine.toEntity(createdAt: Long, updatedAt: Long) = RoutineEntity(
    id = id,
    name = name.trim(),
    goal = goal.trim(),
    mode = mode.name,
    flexibleTimeoutSec = flexibleTimeoutSec,
    interruptionBehavior = interruptionBehavior.name,
    targetDurationSec = targetDurationSec,
    wakeTimeMinutes = wakeTimeMinutes,
    leaveTimeMinutes = leaveTimeMinutes,
    alarmEnabled = alarmEnabled,
    alarmDays = alarmDays,
    musicUrl = music?.url,
    musicTitle = music?.title,
    musicArtist = music?.artist,
    musicArtworkUrl = music?.artworkUrl,
    musicKind = music?.kind?.name,
    musicPlayback = musicPlayback.name,
    musicVolume = musicVolume,
    musicLoop = musicLoop,
    voiceEnabled = announcements.enabled,
    voiceVolume = announcements.volume,
    voiceIncludeDuration = announcements.includeDuration,
    voiceWarningSec = announcements.warningBeforeEndSec,
    voiceStartMessage = announcements.startMessage,
    voiceEndMessage = announcements.endMessage,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun RoutineTask.toEntity() = TaskEntity(
    id = 0,
    routineId = 0,
    position = 0,
    title = title.trim(),
    spokenText = spokenText.trim(),
    durationSec = durationSec,
    gapBeforeSec = gapBeforeSec,
    mandatory = mandatory,
    minDurationSec = minDurationSec,
    notes = notes.trim(),
)

private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
    value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
