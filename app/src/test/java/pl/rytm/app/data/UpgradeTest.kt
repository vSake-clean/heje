package pl.rytm.app.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.rytm.app.data.db.RytmDatabase
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import java.io.File

/**
 * Aktualizacja z wersji 1.0.0: baza danych (schemat v1 → v2) i przykładowe rutyny.
 * Rutyny użytkownika muszą przetrwać aktualizację bez zmian.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpgradeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "upgrade-test.db"

    @Before
    @After
    fun clean() {
        context.deleteDatabase(dbName)
        context.getSharedPreferences("app_meta", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Tworzy bazę dokładnie wg wyeksportowanego schematu v1 (app/schemas/…/1.json). */
    private fun createV1Database(): File {
        val schema = Json.parseToJsonElement(File("schemas/pl.rytm.app.data.db.RytmDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema.getValue("entities").jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o.getValue("tableName").jsonPrimitive.content
            db.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            o["indices"]?.jsonArray?.forEach { i ->
                db.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL(
            "INSERT INTO routines (id, name, goal, mode, flexible_timeout_sec, interruption_behavior, target_duration_sec, " +
                "wake_time_minutes, leave_time_minutes, alarm_enabled, alarm_days, music_url, music_title, music_artist, " +
                "music_artwork_url, music_kind, music_playback, music_volume, music_loop, voice_enabled, voice_volume, " +
                "voice_include_duration, voice_warning_sec, voice_start_message, voice_end_message, last_used_at, created_at, updated_at) " +
                "VALUES (1, 'Moja rutyna', 'cel', 'STRICT', 60, 'PAUSE', NULL, 390, 450, 1, 31, " +
                "'https://soundcloud.com/a/sets/b', 'Lista', 'A', NULL, 'PLAYLIST', 'EMBEDDED', 80, 1, 1, 90, 0, 10, '', 'Koniec', NULL, 1000, 2000)",
        )
        db.execSQL(
            "INSERT INTO tasks (id, routine_id, position, title, spoken_text, duration_sec, gap_before_sec, mandatory, min_duration_sec, notes) " +
                "VALUES (1, 1, 0, 'Wstań', 'Wstań z łóżka.', 20, 10, 1, NULL, '')",
        )
        db.version = 1
        db.close()
        return file
    }

    @Test
    fun `database from 1_0_0 migrates without losing user routines`() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, RytmDatabase::class.java, dbName)
            .addMigrations(RytmDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val routine = RoutineRepository(db.routineDao()).getAll().single()
        assertEquals("Moja rutyna", routine.name)
        assertEquals(390, routine.wakeTimeMinutes)
        assertTrue(routine.alarmEnabled)
        assertEquals("https://soundcloud.com/a/sets/b", routine.music?.url)
        assertNull(routine.music?.embedUrl)
        assertEquals(listOf("Wstań"), routine.tasks.map { it.title })
        db.close()
    }

    @Test
    fun `unchanged 1_0_0 samples are refreshed, edited and own routines are kept`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RytmDatabase::class.java).allowMainThreadQueries().build()
        var clock = 1_000_000L
        val repo = RoutineRepository(db.routineDao()) { clock }
        // Stan po 1.0.0: cztery przykłady, jeden z nich edytowany później przez użytkownika, plus własna rutyna.
        val oldTask = listOf(RoutineTask(title = "Stare", durationSec = 30))
        SampleRoutines.V1_NAMES.forEach { repo.save(Routine(name = it, tasks = oldTask)) }
        val edited = repo.getAll().first { it.name == "Sprzątanie pokoju" }
        clock += 60_000
        repo.save(edited.copy(goal = "mój cel"))
        repo.save(Routine(name = "Moja rutyna", tasks = oldTask))
        val meta = AppMeta(context)

        val added = repo.seedOrUpgradeSamples(meta)
        val all = repo.getAll()
        assertTrue(added >= 5)
        // Edytowany przykład i własna rutyna – bez zmian.
        assertEquals("mój cel", all.single { it.name == "Sprzątanie pokoju" }.goal)
        assertEquals(listOf("Stare"), all.single { it.name == "Sprzątanie pokoju" }.tasks.map { it.title })
        assertEquals(listOf("Stare"), all.single { it.name == "Moja rutyna" }.tasks.map { it.title })
        // Niezmieniony przykład – nowa wersja.
        val morning = all.single { it.name == "Poranne wstawanie" }
        assertEquals(SampleRoutines.morning().tasks.size, morning.tasks.size)
        // Stary przykład bez odpowiednika w nowym zestawie znika; nowe rutyny dodane.
        assertTrue(all.none { it.name == "Przygotowanie do wyjścia" })
        assertTrue(all.any { it.name == "Rozruch – poranne ćwiczenia" })
        assertEquals(SampleRoutines.VERSION, meta.samplesVersion)
        // Ponowne uruchomienie nic nie zmienia.
        assertEquals(0, repo.seedOrUpgradeSamples(meta))
        assertEquals(all.size, repo.getAll().size)
        db.close()
    }

    @Test
    fun `fresh install gets all samples once`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RytmDatabase::class.java).allowMainThreadQueries().build()
        val repo = RoutineRepository(db.routineDao())
        val meta = AppMeta(context)
        assertEquals(SampleRoutines.all().size, repo.seedOrUpgradeSamples(meta))
        assertEquals(0, repo.seedOrUpgradeSamples(meta))
        assertEquals(SampleRoutines.all().size, repo.getAll().size)
        db.close()
    }
}
