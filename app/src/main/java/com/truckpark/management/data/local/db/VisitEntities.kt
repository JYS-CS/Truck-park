package com.truckpark.management.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Plates seen on this handset, used for quick lookup when a truck arrives. */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val plateNumber: String,
    val lastVisitAt: Long,
)

/** Timestamps are epoch milliseconds (UTC). Enum-like columns hold the server codes. */
@Entity(
    tableName = "truck_visits",
    indices = [
        Index(value = ["plateNumber", "status"]),
        Index(value = ["status", "arrivedAt"]),
        Index(value = ["syncState"]),
    ],
)
data class TruckVisitEntity(
    /** Generated on this handset so a retried sync is idempotent on the server. */
    @PrimaryKey val id: String,
    val reference: String?,
    val plateNumber: String,
    val visitType: String,
    val status: String,
    val driverName: String?,
    val driverPhone: String?,
    val arrivalNotes: String?,
    val arrivedAt: Long,
    val departedAt: Long?,
    val departureNotes: String?,
    /** Operator confirmed saving a second open visit for the same plate. */
    val allowDuplicate: Boolean,
    val syncState: String,
    val syncError: String?,
    val rowVersion: Long,
    val updatedAt: Long,
    val cargoDescription: String? = null,
    val cargoJson: String? = null,
    val sealsJson: String? = null,
    val conflictServerJson: String? = null,
    val isEdited: Boolean = false,
)

@Entity(
    tableName = "visit_corrections",
    indices = [Index(value = ["visitId"])],
)
data class VisitCorrectionEntity(
    @PrimaryKey val id: String,
    val visitId: String,
    val editedByName: String?,
    val editedAt: Long,
    val reason: String?,
    val changesSummary: String,
    val wasCritical: Boolean,
    val syncState: String,
)

/** One pending operation to push to the server, processed oldest first. */
@Entity(tableName = "sync_queue", indices = [Index(value = ["visitId"])])
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: String,
    val operation: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val payloadJson: String? = null,
) {
    companion object {
        const val ARRIVE = "ARRIVE"
        const val DEPART = "DEPART"
        const val CORRECT = "CORRECT"
    }
}

object SyncStateCodes {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
}

