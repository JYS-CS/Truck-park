package com.truckpark.management.data.repositories

import android.content.Context
import androidx.room.withTransaction
import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.db.SyncQueueEntity
import com.truckpark.management.data.local.db.SyncStateCodes
import com.truckpark.management.data.local.db.TruckParkDatabase
import com.truckpark.management.data.local.db.TruckVisitEntity
import com.truckpark.management.data.local.db.VehicleEntity
import com.truckpark.management.data.local.db.VisitCorrectionEntity
import com.truckpark.management.data.remote.visits.ArrivalPayload
import com.truckpark.management.data.remote.visits.CorrectionPayload
import com.truckpark.management.data.remote.visits.RemoteVisit
import com.truckpark.management.data.remote.visits.VisitsRemoteDataSource
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.CargoItem
import com.truckpark.management.domain.models.MAX_PLATE_LENGTH
import com.truckpark.management.domain.models.SyncReport
import com.truckpark.management.domain.models.SyncState
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitCorrectionDraft
import com.truckpark.management.domain.models.VisitCorrectionRecord
import com.truckpark.management.domain.models.VisitSeal
import com.truckpark.management.domain.models.VisitStatus
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.cleanDisplayNotes
import com.truckpark.management.domain.models.decodeCargoItemsJson
import com.truckpark.management.domain.models.decodeSealsJson
import com.truckpark.management.domain.models.encodeCargoItemsJson
import com.truckpark.management.domain.models.encodeCustomFieldsTags
import com.truckpark.management.domain.models.encodeSealsJson
import com.truckpark.management.domain.models.extractPlateInfo
import com.truckpark.management.domain.models.normalizePlate
import com.truckpark.management.domain.models.sanitizeTagPart
import com.truckpark.management.domain.repositories.AuthRepository
import com.truckpark.management.domain.repositories.VisitsRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

interface LastSyncStore {
    fun getLastSyncedAt(): Instant?
    fun setLastSyncedAt(instant: Instant)
}

class PrefsLastSyncStore(context: Context) : LastSyncStore {
    private val prefs = context.applicationContext.getSharedPreferences("truckpark_sync_prefs", Context.MODE_PRIVATE)

    override fun getLastSyncedAt(): Instant? {
        val millis = prefs.getLong(KEY_LAST_SYNCED_MS, -1L)
        return if (millis > 0L) Instant.ofEpochMilli(millis) else null
    }

    override fun setLastSyncedAt(instant: Instant) {
        prefs.edit().putLong(KEY_LAST_SYNCED_MS, instant.toEpochMilli()).apply()
    }

    private companion object {
        const val KEY_LAST_SYNCED_MS = "last_synced_at_ms"
    }
}

class VisitsRepositoryImpl(
    private val db: TruckParkDatabase,
    private val remote: VisitsRemoteDataSource,
    private val auth: AuthRepository,
    private val lastSyncStore: LastSyncStore? = null,
    private val clock: () -> Instant = Instant::now,
) : VisitsRepository {

    private val visits = db.visitDao()
    private val corrections = db.visitCorrectionDao()
    private val vehicles = db.vehicleDao()
    private val queue = db.syncQueueDao()
    private val syncLock = Mutex()

    private val _lastSyncedAt = MutableStateFlow(lastSyncStore?.getLastSyncedAt())

    override fun observeOpenVisits(): Flow<List<TruckVisit>> =
        visits.observeOpen().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentCompleted(): Flow<List<TruckVisit>> {
        val since = clock().minus(Duration.ofDays(RECENT_DAYS)).toEpochMilli()
        return visits.observeCompletedSince(since).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observePendingCount(): Flow<Int> = queue.observePendingCount()

    override fun observeFailingSyncOps(minAttempts: Int): Flow<List<SyncQueueEntity>> =
        queue.observeFailingOps(minAttempts)

    override fun observeFailedAndConflict(): Flow<List<TruckVisit>> =
        visits.observeFailedAndConflict().map { rows -> rows.map { it.toDomain() } }

    override fun observeLastSyncedAt(): Flow<Instant?> = _lastSyncedAt.asStateFlow()

    override fun observeVisitHistory(visitId: String): Flow<List<VisitCorrectionRecord>> =
        corrections.observeForVisit(visitId).map { rows ->
            rows.map {
                VisitCorrectionRecord(
                    id = it.id,
                    visitId = it.visitId,
                    editedByName = it.editedByName,
                    editedAt = Instant.ofEpochMilli(it.editedAt),
                    reason = it.reason,
                    changesSummary = it.changesSummary,
                    wasCritical = it.wasCritical,
                )
            }
        }

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
            val localOpen = visits.findOpenByPlate(plate)
            if (localOpen != null) {
                return ArrivalOutcome.OpenVisitExists(localOpen.toDomain())
            }
        }

        val visitId = UUID.randomUUID().toString()
        val now = clock().toEpochMilli()
        val plateInfo = extractPlateInfo(plate, draft.notes)
        val cleanCargoDesc = draft.cargoDescription.cleaned()
        val encodedCargo = encodeCargoItemsJson(draft.cargoItems)
        val encodedSeals = encodeSealsJson(draft.seals)

        val payload = ArrivalPayload(
            visitId = visitId,
            plateNumber = plate,
            visitType = draft.visitType.code,
            driverName = draft.driverName.cleaned(),
            driverPhone = draft.driverPhone.cleaned(),
            notes = draft.notes.cleaned(),
            arrivedAt = Instant.ofEpochMilli(now),
            allowDuplicate = allowDuplicate,
            countryCode = plateInfo.country,
            frontPlate = plateInfo.frontPlate,
            horsePlate = plateInfo.horsePlate,
            transporter = plateInfo.transporter,
            cargoDescription = cleanCargoDesc,
            cargoItems = draft.cargoItems,
            seals = draft.seals,
        )

        val cloudResult = auth.authorised { token ->
            remote.arrive(token, payload)
        }

        return when (cloudResult) {
            is AuthResult.Success -> {
                val serverVisit = cloudResult.value
                val entity = serverVisit.toEntity(
                    existing = TruckVisitEntity(
                        id = visitId,
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
                        syncState = SyncStateCodes.SYNCED,
                        syncError = null,
                        rowVersion = 1L,
                        updatedAt = now,
                        cargoDescription = cleanCargoDesc,
                        cargoJson = encodedCargo,
                        sealsJson = encodedSeals,
                    ),
                ).copy(
                    allowDuplicate = allowDuplicate,
                    syncState = SyncStateCodes.SYNCED,
                    syncError = null,
                )
                db.withTransaction {
                    visits.upsert(entity)
                    vehicles.upsert(VehicleEntity(plate, now))
                }
                markSyncedNow()
                ArrivalOutcome.Saved(entity.toDomain())
            }
            is AuthResult.Failure -> {
                val error = cloudResult.error
                if (!allowDuplicate && error.code == OPEN_VISIT_EXISTS) {
                    val existingLocal = visits.findOpenByPlate(plate)?.toDomain()
                        ?: TruckVisit(
                            id = "remote-$plate",
                            reference = null,
                            plateNumber = plate,
                            visitType = draft.visitType,
                            status = VisitStatus.Open,
                            driverName = null,
                            driverPhone = null,
                            arrivalNotes = null,
                            arrivedAt = Instant.ofEpochMilli(now),
                            departedAt = null,
                            departureNotes = null,
                            syncState = SyncState.Synced,
                            syncError = error.message,
                        )
                    return ArrivalOutcome.OpenVisitExists(existingLocal)
                }

                if (error.httpStatus != null && error.httpStatus in 400..499 &&
                    !error.endsSession && error.code != AuthError.UNAUTHORIZED
                ) {
                    return ArrivalOutcome.Invalid(error.message)
                }

                // Offline or transient failure: save locally as PENDING and queue for auto-sync
                val entity = TruckVisitEntity(
                    id = visitId,
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
                    syncError = error.message,
                    rowVersion = 0,
                    updatedAt = now,
                    cargoDescription = cleanCargoDesc,
                    cargoJson = encodedCargo,
                    sealsJson = encodedSeals,
                )
                db.withTransaction {
                    visits.upsert(entity)
                    vehicles.upsert(VehicleEntity(plate, now))
                    queue.insert(SyncQueueEntity(visitId = entity.id, operation = SyncQueueEntity.ARRIVE, createdAt = now))
                }
                ArrivalOutcome.Saved(entity.toDomain())
            }
        }
    }

    override suspend fun recordDeparture(
        visitId: String,
        notes: String?,
        updatedSeals: List<VisitSeal>?,
    ): AuthResult<TruckVisit> {
        val now = clock().toEpochMilli()
        val current = visits.getById(visitId)
            ?: return failure(NOT_FOUND, "That visit could not be found.")
        if (current.status == STATUS_COMPLETED) {
            return failure(ALREADY_COMPLETED, "This visit has already been completed.")
        }
        val cleanNotes = notes.cleaned()
        val updatedSealsJson = if (updatedSeals != null) encodeSealsJson(updatedSeals) else current.sealsJson
        val departedInstant = Instant.ofEpochMilli(now)

        val cloudResult = auth.authorised { token ->
            remote.depart(token, visitId, departedInstant, cleanNotes)
        }

        return when (cloudResult) {
            is AuthResult.Success -> {
                val serverVisit = cloudResult.value
                val updated = serverVisit.toEntity(current).copy(
                    status = STATUS_COMPLETED,
                    departedAt = now,
                    departureNotes = cleanNotes,
                    sealsJson = updatedSealsJson,
                    syncState = SyncStateCodes.SYNCED,
                    syncError = null,
                    updatedAt = now,
                )
                db.withTransaction {
                    visits.update(updated)
                }
                markSyncedNow()
                AuthResult.Success(updated.toDomain())
            }
            is AuthResult.Failure -> {
                val error = cloudResult.error
                if (error.httpStatus != null && error.httpStatus in 400..499 &&
                    !error.endsSession && error.code != AuthError.UNAUTHORIZED
                ) {
                    return AuthResult.Failure(error)
                }

                // Offline fallback: save locally as COMPLETED and queue for auto-sync
                val updated = current.copy(
                    status = STATUS_COMPLETED,
                    departedAt = now,
                    departureNotes = cleanNotes,
                    sealsJson = updatedSealsJson,
                    syncState = SyncStateCodes.PENDING,
                    syncError = error.message,
                    updatedAt = now,
                )
                db.withTransaction {
                    visits.update(updated)
                    queue.insert(SyncQueueEntity(visitId = visitId, operation = SyncQueueEntity.DEPART, createdAt = now))
                }
                AuthResult.Success(updated.toDomain())
            }
        }
    }

    override suspend fun recordCorrection(
        draft: VisitCorrectionDraft,
        canCorrectCritical: Boolean,
        editorName: String?,
    ): AuthResult<TruckVisit> {
        val current = visits.getById(draft.visitId)
            ?: return failure(NOT_FOUND, "That visit could not be found.")
        val isCompleted = current.status == STATUS_COMPLETED
        if (isCompleted && !canCorrectCritical) {
            return failure("NOT_AUTHORIZED", "Only a Supervisor or Admin can correct a completed visit.")
        }
        val cleanReason = draft.reason.cleaned()
        if (isCompleted && cleanReason == null) {
            return failure("INVALID_REQUEST", "Please enter a reason for correcting a completed visit.")
        }

        val cleanCountry = draft.countryCode.trim().uppercase().ifBlank { "ZM" }.take(4)
        val cleanFront = normalizePlate(draft.frontPlate)
        val cleanHorse = normalizePlate(draft.horsePlate).ifBlank { cleanFront }
        if (cleanFront.isBlank()) {
            return failure("INVALID_REQUEST", "Front plate registration is required.")
        }
        if (cleanFront.length > MAX_PLATE_LENGTH || cleanHorse.length > MAX_PLATE_LENGTH) {
            return failure("INVALID_REQUEST", "Registration plate is too long.")
        }

        val cleanTransporter = draft.transporter.cleaned()
        val oldInfo = extractPlateInfo(current.plateNumber, current.arrivalNotes, current.departureNotes)
        val customPurpose = (draft.customPurpose.cleaned() ?: oldInfo.customPurpose)
            ?.takeIf { draft.visitType == VisitType.Other }
        val customCountryReason = draft.customCountryReason.cleaned()
        val exitVerifiedPlate = (draft.exitVerifiedPlate.cleaned() ?: oldInfo.exitVerifiedPlate)
            ?.let { normalizePlate(it) }
            ?.takeIf { it.isNotBlank() }
        val customFieldsTags = encodeCustomFieldsTags(draft.customFields)
        val cleanUserNotes = draft.arrivalNotes.cleaned().orEmpty()
        val rebuiltNotes = buildString {
            append("[Country: $cleanCountry] [Front: $cleanFront] [Horse: $cleanHorse]")
            if (customCountryReason != null) append(" [CountryReason: ${sanitizeTagPart(customCountryReason)}]")
            if (cleanTransporter != null) append(" [Transporter: ${sanitizeTagPart(cleanTransporter)}]")
            if (customPurpose != null) append(" [Purpose: ${sanitizeTagPart(customPurpose)}]")
            if (exitVerifiedPlate != null) append(" [ExitPlate: ${sanitizeTagPart(exitVerifiedPlate)}]")
            if (customFieldsTags.isNotBlank()) append(" $customFieldsTags")
            if (cleanUserNotes.isNotBlank()) append(" $cleanUserNotes")
        }.trim().takeIf { it.isNotBlank() }

        val cleanDepNotes = if (isCompleted) {
            val rawDep = cleanDisplayNotes(draft.departureNotes).orEmpty()
            buildString {
                if (exitVerifiedPlate != null) append("[ExitPlate: ${sanitizeTagPart(exitVerifiedPlate)}] ")
                append(rawDep)
            }.trim().takeIf { it.isNotBlank() }
        } else {
            current.departureNotes
        }
        val cleanCargoDesc = draft.cargoDescription.cleaned()
        val encodedCargo = encodeCargoItemsJson(draft.cargoItems)
        val encodedSeals = encodeSealsJson(draft.seals)
        val summary = buildCorrectionSummary(
            old = current,
            oldInfo = oldInfo,
            newCountry = cleanCountry,
            newCountryReason = customCountryReason,
            newFront = cleanFront,
            newHorse = cleanHorse,
            newTransporter = cleanTransporter,
            newType = draft.visitType,
            newCustomPurpose = customPurpose,
            newDriver = draft.driverName.cleaned(),
            newCargoDesc = cleanCargoDesc,
            newSeals = draft.seals,
            newCustomFields = draft.customFields,
        )

        val now = clock().toEpochMilli()
        val localHistory = VisitCorrectionEntity(
            id = UUID.randomUUID().toString(),
            visitId = current.id,
            editedByName = editorName ?: "Staff",
            editedAt = now,
            reason = cleanReason,
            changesSummary = summary,
            wasCritical = isCompleted,
            syncState = SyncStateCodes.PENDING,
        )

        val locallyUpdated = current.copy(
            plateNumber = cleanFront,
            visitType = draft.visitType.code,
            driverName = draft.driverName.cleaned(),
            driverPhone = draft.driverPhone.cleaned(),
            arrivalNotes = rebuiltNotes,
            departureNotes = cleanDepNotes,
            cargoDescription = cleanCargoDesc,
            cargoJson = encodedCargo,
            sealsJson = encodedSeals,
            isEdited = true,
            updatedAt = now,
        )

        val payload = CorrectionPayload(
            visitId = current.id,
            plateNumber = cleanFront,
            countryCode = cleanCountry,
            frontPlate = cleanFront,
            horsePlate = cleanHorse,
            transporter = cleanTransporter,
            visitType = draft.visitType.code,
            driverName = draft.driverName.cleaned(),
            driverPhone = draft.driverPhone.cleaned(),
            arrivalNotes = rebuiltNotes,
            departureNotes = cleanDepNotes,
            cargoDescription = cleanCargoDesc,
            cargoItems = draft.cargoItems,
            seals = draft.seals,
            reason = cleanReason,
            expectedRowVersion = current.rowVersion.takeIf { it > 0L },
        )

        val cloudResult = if (current.reference != null) {
            auth.authorised { token -> remote.correct(token, payload) }
        } else {
            null
        }

        return when (cloudResult) {
            is AuthResult.Success -> {
                val synced = cloudResult.value.toEntity(locallyUpdated).copy(
                    syncState = SyncStateCodes.SYNCED,
                    syncError = null,
                    conflictServerJson = null,
                    isEdited = true,
                )
                db.withTransaction {
                    visits.upsert(synced)
                    corrections.insert(localHistory.copy(syncState = SyncStateCodes.SYNCED))
                }
                refreshVisitHistory(current.id)
                markSyncedNow()
                AuthResult.Success(synced.toDomain())
            }
            is AuthResult.Failure -> {
                val error = cloudResult.error
                if (error.code == VERSION_CONFLICT || error.httpStatus == 409) {
                    val serverSnapshot = fetchServerSnapshot(current.id)
                    db.withTransaction {
                        visits.upsert(locallyUpdated)
                        corrections.insert(localHistory)
                        visits.markConflict(current.id, error.message, serverSnapshot)
                    }
                    return AuthResult.Failure(error)
                }
                if (error.httpStatus != null && error.httpStatus in 400..499 &&
                    !error.endsSession && error.code != AuthError.UNAUTHORIZED
                ) {
                    return AuthResult.Failure(error)
                }

                // Offline/transient fallback: keep edit locally & queue CORRECT
                val pending = locallyUpdated.copy(
                    syncState = SyncStateCodes.PENDING,
                    syncError = error.message,
                )
                db.withTransaction {
                    visits.upsert(pending)
                    corrections.insert(localHistory)
                    queue.insert(
                        SyncQueueEntity(
                            visitId = current.id,
                            operation = SyncQueueEntity.CORRECT,
                            createdAt = now,
                            payloadJson = JSONObject().apply {
                                cleanReason?.let { put("reason", it) }
                            }.toString(),
                        ),
                    )
                }
                AuthResult.Success(pending.toDomain())
            }
            null -> {
                // Visit hasn't synced its initial arrival yet; updating the local entity suffices
                // because ARRIVE sync reads the latest fields from the entity!
                val pending = locallyUpdated.copy(syncState = SyncStateCodes.PENDING)
                db.withTransaction {
                    visits.upsert(pending)
                    corrections.insert(localHistory)
                }
                AuthResult.Success(pending.toDomain())
            }
        }
    }

    override suspend fun refreshVisitHistory(visitId: String) {
        val result = auth.authorised { token -> remote.listHistory(token, visitId) }
        if (result is AuthResult.Success) {
            val entities = result.value.map {
                VisitCorrectionEntity(
                    id = it.id,
                    visitId = it.visitId,
                    editedByName = it.editedByName,
                    editedAt = it.editedAt.toEpochMilli(),
                    reason = it.reason,
                    changesSummary = it.changesSummary,
                    wasCritical = it.wasCritical,
                    syncState = SyncStateCodes.SYNCED,
                )
            }
            corrections.upsertAll(entities)
        }
    }

    override suspend fun resolveConflictKeepLocal(visitId: String): AuthResult<TruckVisit> {
        val current = visits.getById(visitId)
            ?: return failure(NOT_FOUND, "Visit not found.")
        val info = extractPlateInfo(current.plateNumber, current.arrivalNotes)
        val result = auth.authorised { token ->
            if (current.reference == null) {
                remote.arrive(token, current.toArrivalPayload(forceAllowDuplicate = true))
            } else {
                remote.correct(
                    token,
                    CorrectionPayload(
                        visitId = current.id,
                        plateNumber = current.plateNumber,
                        countryCode = info.country,
                        frontPlate = info.frontPlate,
                        horsePlate = info.horsePlate,
                        transporter = info.transporter,
                        visitType = current.visitType,
                        driverName = current.driverName,
                        driverPhone = current.driverPhone,
                        arrivalNotes = current.arrivalNotes,
                        departureNotes = current.departureNotes,
                        cargoDescription = current.cargoDescription,
                        cargoItems = decodeCargoItemsJson(current.cargoJson),
                        seals = decodeSealsJson(current.sealsJson),
                        reason = "Supervisor conflict resolution: kept handset version",
                        expectedRowVersion = null,
                    ),
                )
            }
        }

        return when (result) {
            is AuthResult.Success -> {
                val synced = result.value.toEntity(current).copy(
                    allowDuplicate = true,
                    syncState = SyncStateCodes.SYNCED,
                    syncError = null,
                    conflictServerJson = null,
                )
                db.withTransaction {
                    queue.deleteForVisit(visitId)
                    visits.upsert(synced)
                }
                markSyncedNow()
                AuthResult.Success(synced.toDomain())
            }
            is AuthResult.Failure -> AuthResult.Failure(result.error)
        }
    }

    override suspend fun resolveConflictUseServer(visitId: String): AuthResult<Unit> {
        val result = auth.authorised { token -> remote.list(token) }
        return when (result) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> {
                val remoteMatch = result.value.firstOrNull { it.id == visitId }
                val existing = visits.getById(visitId)
                db.withTransaction {
                    queue.deleteForVisit(visitId)
                    if (remoteMatch != null) {
                        visits.upsert(
                            remoteMatch.toEntity(existing).copy(
                                syncState = SyncStateCodes.SYNCED,
                                syncError = null,
                                conflictServerJson = null,
                            ),
                        )
                    } else if (existing != null) {
                        visits.resolveConflictKeepSynced(visitId, SyncStateCodes.SYNCED)
                    }
                }
                markSyncedNow()
                AuthResult.Success(Unit)
            }
        }
    }

    override suspend fun sync(): SyncReport = syncLock.withLock {
        var synced = 0
        var failed = 0
        var conflicts = 0
        var offline = false
        var signedOut = false
        var lastErrorMessage: String? = null

        loop@ for (op in queue.runnable()) {
            val visit = visits.getById(op.visitId)
            if (visit == null) {
                queue.delete(op.id)
                continue@loop
            }
            if (visit.syncState == SyncStateCodes.FAILED || visit.syncState == SyncStateCodes.CONFLICT) {
                // Held until explicit user retry or Supervisor conflict review
                continue@loop
            }

            val result = auth.authorised { token ->
                when (op.operation) {
                    SyncQueueEntity.ARRIVE -> remote.arrive(token, visit.toArrivalPayload())
                    SyncQueueEntity.CORRECT -> {
                        val info = extractPlateInfo(visit.plateNumber, visit.arrivalNotes)
                        val reason = op.payloadJson?.let {
                            try {
                                JSONObject(it).optString("reason").takeIf { r -> r.isNotBlank() }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        remote.correct(
                            token,
                            CorrectionPayload(
                                visitId = visit.id,
                                plateNumber = visit.plateNumber,
                                countryCode = info.country,
                                frontPlate = info.frontPlate,
                                horsePlate = info.horsePlate,
                                transporter = info.transporter,
                                visitType = visit.visitType,
                                driverName = visit.driverName,
                                driverPhone = visit.driverPhone,
                                arrivalNotes = visit.arrivalNotes,
                                departureNotes = visit.departureNotes,
                                cargoDescription = visit.cargoDescription,
                                cargoItems = decodeCargoItemsJson(visit.cargoJson),
                                seals = decodeSealsJson(visit.sealsJson),
                                reason = reason,
                                expectedRowVersion = visit.rowVersion.takeIf { it > 0L },
                            ),
                        )
                    }
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
                    lastErrorMessage = error.message
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
                        error.code == OPEN_VISIT_EXISTS || error.code == VERSION_CONFLICT -> {
                            queue.delete(op.id)
                            val snapshot = fetchServerSnapshot(visit.id)
                            visits.markConflict(visit.id, error.message, snapshot)
                            conflicts++
                        }
                        error.httpStatus != null && error.httpStatus in 400..499 -> {
                            queue.recordAttempt(op.id, error.message)
                            visits.setSyncState(visit.id, SyncStateCodes.FAILED, error.message)
                            failed++
                        }
                        else -> {
                            // Server trouble (5xx or unreadable reply): keep queued and increment attempts.
                            queue.recordAttempt(op.id, error.message)
                            failed++
                            break@loop
                        }
                    }
                }
            }
        }

        if (!offline && !signedOut && failed == 0) {
            markSyncedNow()
        }
        SyncReport(synced, failed, conflicts, offline, signedOut, lastErrorMessage)
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
                markSyncedNow()
                AuthResult.Success(Unit)
            }
        }
    }

    override suspend fun retryFailed() {
        db.withTransaction {
            visits.resetFailed()
            queue.resetAllAttempts()
        }
    }

    private suspend fun fetchServerSnapshot(visitId: String): String? {
        val listRes = auth.authorised { token -> remote.list(token) }
        if (listRes !is AuthResult.Success) return null
        val match = listRes.value.firstOrNull { it.id == visitId } ?: return null
        return buildString {
            append("Ref #${match.reference} • ${match.visitType} • ${match.status}")
            append(" • Front ${match.frontPlate ?: match.plateNumber}")
            if (!match.horsePlate.isNullOrBlank() && match.horsePlate != match.frontPlate) {
                append(" / Horse ${match.horsePlate}")
            }
            if (!match.driverName.isNullOrBlank()) append(" • Driver ${match.driverName}")
            if (!match.cargoDescription.isNullOrBlank()) append(" • Cargo: ${match.cargoDescription}")
        }
    }

    private fun markSyncedNow() {
        val now = clock()
        _lastSyncedAt.value = now
        lastSyncStore?.setLastSyncedAt(now)
    }

    private fun buildCorrectionSummary(
        old: TruckVisitEntity,
        oldInfo: com.truckpark.management.domain.models.PlateInfo,
        newCountry: String,
        newCountryReason: String?,
        newFront: String,
        newHorse: String,
        newTransporter: String?,
        newType: VisitType,
        newCustomPurpose: String?,
        newDriver: String?,
        newCargoDesc: String?,
        newSeals: List<VisitSeal>,
        newCustomFields: List<com.truckpark.management.domain.models.CustomVisitField>,
    ): String {
        val parts = mutableListOf<String>()
        if (oldInfo.country != newCountry) parts += "Country: ${oldInfo.country} -> $newCountry"
        if (oldInfo.customCountryReason != newCountryReason) {
            parts += "Country detail: ${oldInfo.customCountryReason ?: "None"} -> ${newCountryReason ?: "None"}"
        }
        if (oldInfo.frontPlate != newFront) parts += "Front plate: ${oldInfo.frontPlate} -> $newFront"
        if (oldInfo.horsePlate != newHorse) parts += "Horse plate: ${oldInfo.horsePlate} -> $newHorse"
        if (oldInfo.transporter != newTransporter) parts += "Transporter updated"
        if (old.visitType != newType.code) parts += "Type: ${old.visitType} -> ${newType.code}"
        if (oldInfo.customPurpose != newCustomPurpose) {
            parts += "Purpose: ${oldInfo.customPurpose ?: "Standard"} -> ${newCustomPurpose ?: "Standard"}"
        }
        if (old.driverName != newDriver) parts += "Driver updated"
        if (old.cargoDescription != newCargoDesc) parts += "Cargo updated"
        val oldSeals = decodeSealsJson(old.sealsJson)
        if (oldSeals.size != newSeals.size) {
            parts += "Seals: ${oldSeals.size} -> ${newSeals.size}"
        } else if (oldSeals != newSeals) {
            parts += "Seal details/conditions updated"
        }
        val oldCustomMap = oldInfo.customFields.associate { "${it.category.code}:${it.columnName}" to it.value }
        val newCustomMap = newCustomFields.associate { "${it.category.code}:${it.columnName}" to it.value }
        if (oldCustomMap != newCustomMap) {
            val changedColumns = (oldCustomMap.keys + newCustomMap.keys)
                .filter { oldCustomMap[it] != newCustomMap[it] }
                .map { it.substringAfter(":") }
                .distinct()
            if (changedColumns.isNotEmpty()) {
                parts += "Custom columns (${changedColumns.joinToString(", ")})"
            } else {
                parts += "Custom columns updated"
            }
        }
        return if (parts.isEmpty()) "Updated visit notes or details" else parts.joinToString(" • ")
    }

    private fun <T> failure(code: String, message: String): AuthResult<T> =
        AuthResult.Failure(AuthError(code, message))

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_COMPLETED = "COMPLETED"
        const val OPEN_VISIT_EXISTS = "OPEN_VISIT_EXISTS"
        const val VERSION_CONFLICT = "VERSION_CONFLICT"
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
    cargoDescription = cargoDescription,
    cargoItems = decodeCargoItemsJson(cargoJson),
    seals = decodeSealsJson(sealsJson),
    conflictServerSnapshot = conflictServerJson,
    isEdited = isEdited,
    rowVersion = rowVersion,
)

private fun TruckVisitEntity.toArrivalPayload(forceAllowDuplicate: Boolean = allowDuplicate): ArrivalPayload {
    val info = extractPlateInfo(plateNumber, arrivalNotes)
    return ArrivalPayload(
        visitId = id,
        plateNumber = plateNumber,
        visitType = visitType,
        driverName = driverName,
        driverPhone = driverPhone,
        notes = arrivalNotes,
        arrivedAt = Instant.ofEpochMilli(arrivedAt),
        allowDuplicate = forceAllowDuplicate,
        countryCode = info.country,
        frontPlate = info.frontPlate,
        horsePlate = info.horsePlate,
        transporter = info.transporter,
        cargoDescription = cargoDescription,
        cargoItems = decodeCargoItemsJson(cargoJson),
        seals = decodeSealsJson(sealsJson),
    )
}

private fun mergeSealPhotoPaths(
    remoteSeals: List<VisitSeal>,
    localSeals: List<VisitSeal>,
): List<VisitSeal> {
    if (remoteSeals.isEmpty()) return localSeals
    val localByKey = localSeals.associateBy { it.id }.toMutableMap()
    val localByNumber = localSeals.associateBy { it.sealNumber }
    return remoteSeals.map { seal ->
        val localMatch = localByKey[seal.id] ?: localByNumber[seal.sealNumber]
        if (seal.localPhotoPath.isNullOrBlank() && !localMatch?.localPhotoPath.isNullOrBlank()) {
            seal.copy(localPhotoPath = localMatch?.localPhotoPath)
        } else {
            seal
        }
    }
}

private fun RemoteVisit.toEntity(existing: TruckVisitEntity?): TruckVisitEntity {
    val enhancedNotes = if (countryCode != null || frontPlate != null || horsePlate != null || transporter != null) {
        val remoteInfo = extractPlateInfo(plateNumber, arrivalNotes, departureNotes)
        val localInfo = existing?.let { extractPlateInfo(it.plateNumber, it.arrivalNotes, it.departureNotes) }
        val cc = countryCode ?: remoteInfo.country.ifBlank { localInfo?.country ?: "ZM" }
        val fp = frontPlate ?: remoteInfo.frontPlate.ifBlank { localInfo?.frontPlate ?: plateNumber }
        val hp = horsePlate ?: remoteInfo.horsePlate.ifBlank { localInfo?.horsePlate ?: plateNumber }
        val tr = transporter ?: remoteInfo.transporter ?: localInfo?.transporter
        val cr = remoteInfo.customCountryReason ?: localInfo?.customCountryReason
        val cp = remoteInfo.customPurpose ?: localInfo?.customPurpose
        val ep = remoteInfo.exitVerifiedPlate ?: localInfo?.exitVerifiedPlate
        val cf = if (remoteInfo.customFields.isNotEmpty()) remoteInfo.customFields else localInfo?.customFields.orEmpty()
        val cfTags = com.truckpark.management.domain.models.encodeCustomFieldsTags(cf)
        val cleanNotes = cleanDisplayNotes(arrivalNotes) ?: ""
        buildString {
            append("[Country: $cc] [Front: $fp] [Horse: $hp]")
            if (!cr.isNullOrBlank()) append(" [CountryReason: ${com.truckpark.management.domain.models.sanitizeTagPart(cr)}]")
            if (!tr.isNullOrBlank()) append(" [Transporter: ${com.truckpark.management.domain.models.sanitizeTagPart(tr)}]")
            if (!cp.isNullOrBlank()) append(" [Purpose: ${com.truckpark.management.domain.models.sanitizeTagPart(cp)}]")
            if (!ep.isNullOrBlank()) append(" [ExitPlate: ${com.truckpark.management.domain.models.sanitizeTagPart(ep)}]")
            if (cfTags.isNotBlank()) append(" $cfTags")
            if (cleanNotes.isNotBlank()) append(" $cleanNotes")
        }.trim().ifBlank { null }
    } else {
        arrivalNotes
    }

    val existingCargo = existing?.let { decodeCargoItemsJson(it.cargoJson) }.orEmpty()
    val mergedCargo = if (cargoItems.isNotEmpty()) cargoItems else existingCargo
    val existingSeals = existing?.let { decodeSealsJson(it.sealsJson) }.orEmpty()
    val mergedSeals = mergeSealPhotoPaths(seals, existingSeals)

    val remoteArrivedMs = arrivedAt.toEpochMilli()
    val remoteDepartedMs = departedAt?.toEpochMilli()
    val effectiveDepartedMs = when {
        remoteDepartedMs != null && remoteDepartedMs > remoteArrivedMs -> remoteDepartedMs
        existing?.departedAt != null && existing.departedAt > remoteArrivedMs -> existing.departedAt
        else -> remoteDepartedMs ?: existing?.departedAt
    }

    return TruckVisitEntity(
        id = id,
        reference = reference,
        plateNumber = plateNumber,
        visitType = visitType,
        status = status,
        driverName = driverName,
        driverPhone = driverPhone,
        arrivalNotes = enhancedNotes,
        arrivedAt = remoteArrivedMs,
        departedAt = effectiveDepartedMs,
        departureNotes = departureNotes,
        allowDuplicate = existing?.allowDuplicate ?: false,
        syncState = SyncStateCodes.SYNCED,
        syncError = null,
        rowVersion = rowVersion,
        updatedAt = System.currentTimeMillis(),
        cargoDescription = cargoDescription ?: existing?.cargoDescription,
        cargoJson = encodeCargoItemsJson(mergedCargo),
        sealsJson = encodeSealsJson(mergedSeals),
        conflictServerJson = null,
        isEdited = existing?.isEdited ?: false,
    )
}
