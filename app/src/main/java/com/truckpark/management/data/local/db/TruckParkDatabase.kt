package com.truckpark.management.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The offline store. The UI reads only from here; the network only ever updates it.
 * Never use destructive migration: unsynced visits live in this file.
 */
@Database(
    entities = [VehicleEntity::class, TruckVisitEntity::class, SyncQueueEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TruckParkDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        fun create(context: Context): TruckParkDatabase =
            Room.databaseBuilder(context.applicationContext, TruckParkDatabase::class.java, "truckpark.db").build()
    }
}

