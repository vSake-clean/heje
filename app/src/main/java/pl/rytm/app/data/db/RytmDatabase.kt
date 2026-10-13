package pl.rytm.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RoutineEntity::class, TaskEntity::class], version = 2, exportSchema = true)
abstract class RytmDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    companion object {
        /** v1 → v2: adres osadzenia odtwarzacza (prywatne playlisty SoundCloud). Dane użytkownika zostają. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routines ADD COLUMN music_embed_url TEXT")
            }
        }

        fun create(context: Context): RytmDatabase =
            Room.databaseBuilder(context.applicationContext, RytmDatabase::class.java, "rytm.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
