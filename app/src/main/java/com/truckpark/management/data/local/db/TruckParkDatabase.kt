package com.truckpark.management.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The offline store. The UI reads only from here; the network only ever updates it.
 * Never use destructive migration: unsynced visits live in this file.
 */
@Database(
    entities = [
        VehicleEntity::class,
        TruckVisitEntity::class,
        VisitCorrectionEntity::class,
        SyncQueueEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class TruckParkDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao
    abstract fun visitCorrectionDao(): VisitCorrectionDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE truck_visits ADD COLUMN cargoDescription TEXT")
                db.execSQL("ALTER TABLE truck_visits ADD COLUMN cargoJson TEXT")
                db.execSQL("ALTER TABLE truck_visits ADD COLUMN sealsJson TEXT")
                db.execSQL("ALTER TABLE truck_visits ADD COLUMN conflictServerJson TEXT")
                db.execSQL("ALTER TABLE truck_visits ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN payloadJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS visit_corrections (
                        id TEXT NOT NULL PRIMARY KEY,
                        visitId TEXT NOT NULL,
                        editedByName TEXT,
                        editedAt INTEGER NOT NULL,
                        reason TEXT,
                        changesSummary TEXT NOT NULL,
                        wasCritical INTEGER NOT NULL,
                        syncState TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_visit_corrections_visitId ON visit_corrections(visitId)",
                )
            }
        }

        fun create(context: Context): TruckParkDatabase =
            Room.databaseBuilder(context.applicationContext, TruckParkDatabase::class.java, "truckpark.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

