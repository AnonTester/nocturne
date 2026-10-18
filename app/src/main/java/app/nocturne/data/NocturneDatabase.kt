package app.nocturne.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Track::class], version = 1, exportSchema = true)
abstract class NocturneDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    companion object {
        fun create(context: Context): NocturneDatabase =
            Room.databaseBuilder(context, NocturneDatabase::class.java, "nocturne.db").build()
    }
}
