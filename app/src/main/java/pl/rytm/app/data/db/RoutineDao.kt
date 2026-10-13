package pl.rytm.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routines ORDER BY (last_used_at IS NULL), last_used_at DESC, created_at ASC")
    fun observeAll(): Flow<List<RoutineWithTasks>>

    @Transaction
    @Query("SELECT * FROM routines ORDER BY created_at ASC")
    suspend fun getAll(): List<RoutineWithTasks>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: Long): RoutineWithTasks?

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observe(id: Long): Flow<RoutineWithTasks?>

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE routine_id = :routineId")
    suspend fun deleteTasks(routineId: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE routines SET last_used_at = :time WHERE id = :id")
    suspend fun markUsed(id: Long, time: Long)

    @Query("UPDATE routines SET alarm_enabled = :enabled, updated_at = :time WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean, time: Long)

    @Query(
        "UPDATE routines SET music_url = :url, music_title = :title, music_artist = :artist, " +
            "music_artwork_url = :artwork, music_kind = :kind, music_playback = :playback, updated_at = :time WHERE id = :id",
    )
    suspend fun setMusic(
        id: Long,
        url: String?,
        title: String?,
        artist: String?,
        artwork: String?,
        kind: String?,
        playback: String,
        time: Long,
    )

    /** Zapisuje rutynę wraz z zadaniami w jednej transakcji (zadania są zastępowane). */
    @Transaction
    suspend fun upsert(routine: RoutineEntity, tasks: List<TaskEntity>): Long {
        val id = if (routine.id == 0L) {
            insertRoutine(routine)
        } else {
            updateRoutine(routine)
            routine.id
        }
        deleteTasks(id)
        insertTasks(tasks.mapIndexed { i, t -> t.copy(id = 0, routineId = id, position = i) })
        return id
    }
}
