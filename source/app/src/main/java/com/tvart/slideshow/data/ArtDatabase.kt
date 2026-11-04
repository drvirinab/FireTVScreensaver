package com.tvart.slideshow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ImageEntity::class, BatchEntity::class, SlideshowEntity::class,
        MembershipEntity::class, RangeEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ArtDatabase : RoomDatabase() {
    abstract fun dao(): ArtDao

    companion object {
        @Volatile private var instance: ArtDatabase? = null

        fun get(context: Context): ArtDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ArtDatabase::class.java, "tvart.db")
                .build()
                .also { instance = it }
        }
    }
}
