package pl.rytm.app.data

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import pl.rytm.app.alarm.AlarmTimes
import pl.rytm.app.alarm.RoutineAlarmScheduler
import pl.rytm.app.data.db.RytmDatabase
import pl.rytm.app.data.session.SessionStore
import pl.rytm.app.data.settings.SettingsRepository
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.ClockReading
import pl.rytm.app.domain.session.SessionEngine
import pl.rytm.app.domain.session.SessionPlan
import pl.rytm.app.domain.schedule.TimelineBuilder
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Testy integracyjne warstwy Androida (Robolectric): baza Room, zapis sesji, ustawienia, budzik.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: RytmDatabase
    private lateinit var repo: RoutineRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RytmDatabase::class.java).allowMainThreadQueries().build()
        repo = RoutineRepository(db.routineDao())
    }

    @After
    fun tearDown() = db.close()

    private fun sample() = Routine(
        name = "Test",
        goal = "Cel",
        tasks = listOf(
            RoutineTask(title = "A", spokenText = "Raz.", durationSec = 15, gapBeforeSec = 10),
            RoutineTask(title = "B", spokenText = "Dwa.", durationSec = 30, mandatory = false, minDurationSec = 10, notes = "uwaga"),
        ),
        mode = ScheduleMode.FLEXIBLE,
        flexibleTimeoutSec = 45,
        wakeTimeMinutes = 390,
        leaveTimeMinutes = 450,
        alarmEnabled = true,
        music = MusicSelection("https://soundcloud.com/a/b", "Utwór", "Autor", null, MusicKind.TRACK),
        musicVolume = 80,
    )

    @Test
    fun `sample routines are seeded once and match the spec example`() = runBlocking {
        repo.seedIfEmpty()
        repo.seedIfEmpty()
        val all = repo.getAll()
        assertEquals(SampleRoutines.all().size, all.size)
        val morning = all.first { it.name == "Poranne wstawanie" }
        assertEquals(listOf(10, 25, 40, 100, 130), (0..4).map { TimelineBuilder.startSecOf(morning.tasks, it) })
    }

    @Test
    fun `routine with tasks survives save and load`() = runBlocking {
        val id = repo.save(sample())
        val loaded = repo.getRoutine(id)!!
        assertEquals(sample().copy(id = id, tasks = loaded.tasks), loaded)
        assertEquals(listOf("A", "B"), loaded.tasks.map { it.title })
        assertEquals(10, loaded.tasks[0].gapBeforeSec)
        assertEquals(10, loaded.tasks[1].minDurationSec)
        assertFalse(loaded.tasks[1].mandatory)
    }

    @Test
    fun `editing replaces tasks and keeps order`() = runBlocking {
        val id = repo.save(sample())
        val loaded = repo.getRoutine(id)!!
        val edited = loaded.copy(tasks = listOf(loaded.tasks[1], loaded.tasks[0], RoutineTask(title = "C", durationSec = 5)))
        repo.save(edited)
        assertEquals(listOf("B", "A", "C"), repo.getRoutine(id)!!.tasks.map { it.title })
    }

    @Test
    fun `duplicate creates an independent variant`() = runBlocking {
        val id = repo.save(sample())
        val copyId = repo.duplicate(id)!!
        val copy = repo.getRoutine(copyId)!!
        assertEquals("Test (kopia)", copy.name)
        assertFalse(copy.alarmEnabled)
        repo.save(copy.copy(tasks = copy.tasks.take(1)))
        assertEquals(2, repo.getRoutine(id)!!.tasks.size)
    }

    @Test
    fun `deleting a routine deletes its tasks`() = runBlocking {
        val id = repo.save(sample())
        repo.delete(id)
        assertNull(repo.getRoutine(id))
        val c = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tasks")
        c.moveToFirst()
        assertEquals(0, c.getInt(0))
        c.close()
    }

    @Test
    fun `music can be attached from a share`() = runBlocking {
        val id = repo.save(sample().copy(music = null, musicPlayback = MusicPlayback.NONE))
        repo.setMusic(id, MusicSelection("https://soundcloud.com/x/sets/y", "Lista", "X", null, MusicKind.PLAYLIST), MusicPlayback.EMBEDDED)
        val r = repo.getRoutine(id)!!
        assertEquals(MusicKind.PLAYLIST, r.music?.kind)
        assertEquals(MusicPlayback.EMBEDDED, r.effectiveMusicPlayback)
    }

    @Test
    fun `session snapshot is persisted and restored`() {
        val store = SessionStore(context)
        val snap = SessionEngine.start("abc", SessionPlan.from(sample().copy(id = 7)), ClockReading(1_000, 2_000, 3))
        store.save(snap, sync = true)
        assertTrue(store.hasSession())
        assertEquals(snap, SessionStore(context).load())
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `settings are stored locally`() = runBlocking {
        val settings = SettingsRepository(context)
        settings.update { it.copy(duckPercent = 30, defaultMode = ScheduleMode.FLEXIBLE, voiceName = "pl-pl-x-oda-local") }
        val s = settings.current()
        assertEquals(30, s.duckPercent)
        assertEquals(ScheduleMode.FLEXIBLE, s.defaultMode)
        assertEquals("pl-pl-x-oda-local", s.voiceName)
    }

    @Test
    fun `wake-up alarm is scheduled with AlarmManager at the next wake time`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val id = repo.save(sample().copy(alarmDays = Routine.ALL_DAYS))
        val routine = repo.getRoutine(id)!!
        val scheduler = RoutineAlarmScheduler(context, repo)
        val at = scheduler.schedule(routine)
        assertNotNull(at)
        val expected = AlarmTimes.next(ZonedDateTime.now(ZoneId.systemDefault()), 390, Routine.ALL_DAYS)!!.toInstant().toEpochMilli()
        assertEquals(expected, at)
        val am = context.getSystemService(AlarmManager::class.java)
        assertEquals(expected, am.nextAlarmClock?.triggerTime)
        assertEquals(1, shadowOf(am).scheduledAlarms.size)

        scheduler.cancel(id)
        assertTrue(shadowOf(am).scheduledAlarms.isEmpty())
        assertNull(scheduler.schedule(routine.copy(alarmEnabled = false)))
    }
}
