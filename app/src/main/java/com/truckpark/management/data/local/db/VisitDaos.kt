package com.truckpark.management.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM truck_visits WHERE status = 'OPEN' ORDER BY arrivedAt DESC")
    fun observeOpen(): Flow<List<TruckVisitEntity>>

    @Query("SELECT * FROM truck_visits WHERE status = 'COMPLETED' AND departedAt >= :since ORDER BY departedAt DESC")
    fun observeCompletedSince(since: Long): Flow<List<TruckVisitEntity>>

    @Query("SELECT * FROM truck_visits WHERE id = :id")
    suspend fun getById(id: String): TruckVisitEntity?

    @Query("SELECT * FROM truck_visits WHERE plateNumber = :plate AND status = 'OPEN' ORDER BY arrivedAt DESC LIMIT 1")
    suspend fun findOpenByPlate(plate: String): TruckVisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: TruckVisitEntity)

    @Update
    suspend fun update(visit: TruckVisitEntity)

    @Query(
        "UPDATE truck_visits SET reference = :reference, rowVersion = :rowVersion, " +
            "syncState = :syncState, syncError = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: String, reference: String, rowVersion: Long, syncState: String)

    @Query("UPDATE truck_visits SET syncState = :syncState, syncError = :error WHERE id = :id")
    suspend fun setSyncState(id: String, syncState: String, error: String?)

    @Query("UPDATE truck_visits SET syncState = 'PENDING', syncError = NULL WHERE syncState = 'FAILED'")
    suspend fun resetFailed()
}

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("SELECT plateNumber FROM vehicles WHERE plateNumber LIKE :prefix || '%' ORDER BY lastVisitAt DESC LIMIT :limit")
    suspend fun suggest(prefix: String, limit: Int): List<String>
}

@Dao
interface SyncQueueDao {
    /** Oldest first. Visits flagged as conflicting or failed are skipped until a person resolves them. */
    @Query(
        "SELECT q.* FROM sync_queue q JOIN truck_visits v ON v.id = q.visitId " +
            "WHERE v.syncState NOT IN ('CONFLICT', 'FAILED') ORDER BY q.createdAt ASC, q.id ASC",
    )
    suspend fun runnable(): List<SyncQueueEntity>

    @Insert
    suspend fun insert(entry: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordAttempt(id: Long, error: String?)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE visitId = :visitId")
    suspend fun countForVisit(visitId: String): Int

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingCount(): Flow<Int>
}

