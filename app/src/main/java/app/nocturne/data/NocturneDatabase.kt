package app.nocturne.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Track::class], version = 2, exportSchema = true)
abstract class NocturneDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    companion object {
        /** v2: loudness columns (ReplayGain tags / measured). Existing rows start unknown and are filled in by the background scan. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN gainDb REAL")
                db.execSQL("ALTER TABLE tracks ADD COLUMN peak REAL")
                db.execSQL("ALTER TABLE tracks ADD COLUMN gainSource INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_gainSource ON tracks (gainSource)")
            }
        }

        fun create(context: Context): NocturneDatabase =
            Room.databaseBuilder(context, NocturneDatabase::class.java, "nocturne.db").addMigrations(MIGRATION_1_2).build()
    }
}
