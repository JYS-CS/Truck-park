package com.truckpark.management.domain.repositories

import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.db.SyncQueueEntity
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.SyncReport
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitCorrectionDraft
import com.truckpark.management.domain.models.VisitCorrectionRecord
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Truck visits, offline first. Every write lands in the local database and the sync queue
 * immediately; [sync] pushes them to the server when a connection is available.
 */
interface VisitsRepository {
    fun observeOpenVisits(): Flow<List<TruckVisit>>

    /** Visits completed in the last 7 days. */
    fun observeRecentCompleted(): Flow<List<TruckVisit>>

    /** Operations saved on this handset that the server has not confirmed yet. */
    fun observePendingCount(): Flow<Int>

    /** Operations that have failed at least [minAttempts] sync attempts. */
    fun observeFailingSyncOps(minAttempts: Int = 3): Flow<List<SyncQueueEntity>>

    /** Visits stuck in FAILED or CONFLICT state requiring attention or review. */
    fun observeFailedAndConflict(): Flow<List<TruckVisit>>

    /** Last time a full sync/refresh succeeded against the server. */
    fun observeLastSyncedAt(): Flow<Instant?>

    /** Local + synced edit history for a visit. */
    fun observeVisitHistory(visitId: String): Flow<List<VisitCorrectionRecord>>

    suspend fun suggestPlates(prefix: String): List<String>

    suspend fun recordArrival(draft: ArrivalDraft, allowDuplicate: Boolean = false): ArrivalOutcome

    suspend fun recordDeparture(visitId: String, notes: String?): AuthResult<TruckVisit>

    suspend fun recordCorrection(
        draft: VisitCorrectionDraft,
        canCorrectCritical: Boolean,
        editorName: String?,
    ): AuthResult<TruckVisit>

    suspend fun refreshVisitHistory(visitId: String)

    suspend fun resolveConflictKeepLocal(visitId: String): AuthResult<TruckVisit>

    suspend fun resolveConflictUseServer(visitId: String): AuthResult<Unit>

    /** Pushes queued operations to the server, oldest first. Safe to call repeatedly. */
    suspend fun sync(): SyncReport

    /** Pulls the server's open and recent visits. Never overwrites unsynced local changes. */
    suspend fun refresh(): AuthResult<Unit>

    /** Puts rejected items back in the queue and resets attempt counters for a manual retry. */
    suspend fun retryFailed()
}

