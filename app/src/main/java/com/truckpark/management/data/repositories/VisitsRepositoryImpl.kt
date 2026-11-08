package com.truckpark.management.data.repositories

import androidx.room.withTransaction
import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.db.SyncQueueEntity
import com.truckpark.management.data.local.db.SyncStateCodes
import com.truckpark.management.data.local.db.TruckParkDatabase
import com.truckpark.management.data.local.db.TruckVisitEntity
import com.truckpark.management.data.local.db.VehicleEntity
import com.truckpark.management.data.remote.visits.ArrivalPayload
import com.truckpark.management.data.remote.visits.RemoteVisit
import com.truckpark.management.data.remote.visits.VisitsRemoteDataSource
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.MAX_PLATE_LENGTH
import com.truckpark.management.domain.models.SyncReport
import com.truckpark.management.domain.models.SyncState
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitStatus
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.normalizePlate
import com.truckpark.management.domain.repositories.AuthRepository
import com.truckpark.management.domain.repositories.VisitsRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VisitsRepositoryImpl(
    private val db: TruckParkDatabase,
    private val remote: VisitsRemoteDataSource,
    private val auth: AuthRepository,
    private val clock: () -> Instant = Instant::now,
) : VisitsRepository {

    private val visits = db.visitDao()
    private val vehicles = db.vehicleDao()
    private val queue = db.syncQueueDao()
    private val syncLock = Mutex()

    override fun observeOpenVisits(): Flow<List<TruckVisit>> =
        visits.observeOpen().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentCompleted(): Flow<List<TruckVisit>> {
        val since = clock().minus(Duration.ofDays(RECENT_DAYS)).toEpochMilli()
        return visits.observeCompletedSince(since).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observePendingCount(): Flow<Int> = queue.observePendingCount()

    override suspend fun suggestPlates(prefix: String): List<String> {
        val clean = normalizePlate(prefix)
        if (clean.isEmpty()) return emptyList()
        return vehicles.suggest(clean, SUGGESTION_LIMIT)
    }

    override suspend fun recordArrival(draft: ArrivalDraft, allowDuplicate: Boolean): ArrivalOutcome {
        val plate = normalizePlate(draft.plateNumber)
        if (plate.isEmpty()) return ArrivalOutcome.Invalid("Enter the truck's registration number.")
        if (plate.length > MAX_PLATE_LENGTH) return ArrivalOutcome.Invalid("That registration number is too long.")

        if (!allowDuplicate) {
            visits.findOpenByPlate(plate)?.let { return ArrivalOutcome.OpenVisitExists(it.toDomain()) }
        }

        val now = clock().toEpochMilli()
        val entity = TruckVisitEntity(
            id = UUID.randomUUID().toString(),
            reference = null,
            plateNumber = plate,
            visitType = draft.visitType.code,
            status = STATUS_OPEN,
            driverName = draft.driverName.cleaned(),
            driverPhone = draft.driverPhone.cleaned(),
            arrivalNotes = draft.notes.cleaned(),
            arrivedAt = now,
            departedAt = null,
            departureNotes = null,
            allowDuplicate = allowDuplicate,
            syncState = SyncStateCodes.PENDING,
            syncError = null,
            rowVersion = 0,
            updatedAt = now,
        )
        db.withTransaction {
            visits.upsert(entity)
            vehicles.upsert(VehicleEntity(plate, now))
            queue.insert(SyncQueueEntity(visitId = entity.id, operation = SyncQueueEntity.ARRIVE, createdAt = now))
        }
        return ArrivalOutcome.Saved(entity.toDomain())
    }

    override suspend fun recordDeparture(visitId: String, notes: String?): AuthResult<TruckVisit> {
        val now = clock().toEpochMilli()
        return db.withTransaction {
            val current = visits.getById(visitId)
                ?: return@withTransaction failure<TruckVisit>(NOT_FOUND, "That visit could not be found.")
            if (current.status == STATUS_COMPLETED) {
                return@withTransaction failure<TruckVisit>(ALREADY_COMPLETED, "This visit has already been completed.")
            }
            val updated = current.copy(
                status = STATUS_COMPLETED,
                departedAt = now,
                departureNotes = notes.cleaned(),
                syncState = SyncStateCodes.PENDING,
                syncError = null,
                updatedAt = now,
            )
            visits.update(updated)
            queue.insert(SyncQueueEntity(visitId = visitId, operation = SyncQueueEntity.DEPART, createdAt = now))
            AuthResult.Success(updated.toDomain())
        }
    }

    override suspend fun sync(): SyncReport = syncLock.withLock {
        var synced = 0
        var failed = 0
        var conflicts = 0
        var offline = false
        var signedOut = false

        loop@ for (op in queue.runnable()) {
            val visit = visits.getById(op.visitId)
            if (visit == null) {
                queue.delete(op.id)
                continue@loop
            }
            // An earlier operation on the same visit may have just failed in this pass.
            if (visit.syncState == SyncStateCodes.CONFLICT || visit.syncState == SyncStateCodes.FAILED) continue@loop

            val result = auth.authorised { token ->
                when (op.operation) {
                    SyncQueueEntity.ARRIVE -> remote.arrive(token, visit.toArrivalPayload())
                    else -> remote.depart(
                        token,
                        visit.id,
                        Instant.ofEpochMilli(visit.departedAt ?: clock().toEpochMilli()),
                        visit.departureNotes,
                    )
                }
            }

            when (result) {
                is AuthResult.Success -> {
                    queue.delete(op.id)
                    val state = if (queue.countForVisit(visit.id) > 0) SyncStateCodes.PENDING else SyncStateCodes.SYNCED
                    visits.markSynced(visit.id, result.value.reference, result.value.rowVersion, state)
                    synced++
                }
                is AuthResult.Failure -> {
                    val error = result.error
                    when {
                        error.code == AuthError.NETWORK -> {
                            queue.recordAttempt(op.id, error.message)
                            offline = true
                            break@loop
                        }
                        error.endsSession || error.code == AuthError.UNAUTHORIZED || error.code == AuthError.NOT_SIGNED_IN -> {
                            queue.recordAttempt(op.id, error.message)
                            signedOut = true
                            break@loop
                        }
                        error.code == OPEN_VISIT_EXISTS -> {
                            // Another handset logged this truck in first. Keep both versions for a Supervisor.
                            queue.recordAttempt(op.id, error.message)
                            visits.setSyncState(visit.id, SyncStateCodes.CONFLICT, error.message)
                            conflicts++
                        }
                        error.httpStatus != null && error.httpStatus in 400..499 -> {
                            queue.recordAttempt(op.id, error.message)
                            visits.setSyncState(visit.id, SyncStateCodes.FAILED, error.message)
                            failed++
                        }
                        else -> {
                            // Server trouble (5xx or unreadable reply): leave it queued and try again later.
                            queue.recordAttempt(op.id, error.message)
                            failed++
                            break@loop
                        }
                    }
                }
            }
        }
        SyncReport(synced, failed, conflicts, offline, signedOut)
    }

    override suspend fun refresh(): AuthResult<Unit> {
        return when (val result = auth.authorised { token -> remote.list(token) }) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> {
                db.withTransaction {
                    for (item in result.value) {
                        val existing = visits.getById(item.id)
                        // Unsynced local work always wins until it has been pushed or reviewed.
                        if (existing != null && existing.syncState != SyncStateCodes.SYNCED) continue
                        visits.upsert(item.toEntity(existing))
                        vehicles.upsert(VehicleEntity(item.plateNumber, item.arrivedAt.toEpochMilli()))
                    }
                }
                AuthResult.Success(Unit)
            }
        }
    }

    override suspend fun retryFailed() {
        visits.resetFailed()
    }

    private fun <T> failure(code: String, message: String): AuthResult<T> =
        AuthResult.Failure(AuthError(code, message))

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_COMPLETED = "COMPLETED"
        const val OPEN_VISIT_EXISTS = "OPEN_VISIT_EXISTS"
        const val NOT_FOUND = "NOT_FOUND"
        const val ALREADY_COMPLETED = "ALREADY_COMPLETED"
        const val RECENT_DAYS = 7L
        const val SUGGESTION_LIMIT = 6
    }
}

private fun TruckVisitEntity.toDomain(): TruckVisit = TruckVisit(
    id = id,
    reference = reference,
    plateNumber = plateNumber,
    visitType = VisitType.fromCode(visitType),
    status = if (status == "COMPLETED") VisitStatus.Completed else VisitStatus.Open,
    driverName = driverName,
    driverPhone = driverPhone,
    arrivalNotes = arrivalNotes,
    arrivedAt = Instant.ofEpochMilli(arrivedAt),
    departedAt = departedAt?.let { Instant.ofEpochMilli(it) },
    departureNotes = departureNotes,
    syncState = when (syncState) {
        SyncStateCodes.SYNCED -> SyncState.Synced
        SyncStateCodes.CONFLICT -> SyncState.Conflict
        SyncStateCodes.FAILED -> SyncState.Failed
        else -> SyncState.Pending
    },
    syncError = syncError,
)

private fun TruckVisitEntity.toArrivalPayload(): ArrivalPayload = ArrivalPayload(
    visitId = id,
    plateNumber = plateNumber,
    visitType = visitType,
    driverName = driverName,
    driverPhone = driverPhone,
    notes = arrivalNotes,
    arrivedAt = Instant.ofEpochMilli(arrivedAt),
    allowDuplicate = allowDuplicate,
)

private fun RemoteVisit.toEntity(existing: TruckVisitEntity?): TruckVisitEntity = TruckVisitEntity(
    id = id,
    reference = reference,
    plateNumber = plateNumber,
    visitType = visitType,
    status = status,
    driverName = driverName,
    driverPhone = driverPhone,
    arrivalNotes = arrivalNotes,
    arrivedAt = arrivedAt.toEpochMilli(),
    departedAt = departedAt?.toEpochMilli(),
    departureNotes = departureNotes,
    allowDuplicate = existing?.allowDuplicate ?: false,
    syncState = SyncStateCodes.SYNCED,
    syncError = null,
    rowVersion = rowVersion,
    updatedAt = System.currentTimeMillis(),
)
