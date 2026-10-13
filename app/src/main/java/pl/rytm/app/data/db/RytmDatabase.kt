package pl.rytm.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RoutineEntity::class, TaskEntity::class], version = 1, exportSchema = true)
abstract class RytmDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    companion object {
        fun create(context: Context): RytmDatabase =
            Room.databaseBuilder(context.applicationContext, RytmDatabase::class.java, "rytm.db").build()
    }
}
