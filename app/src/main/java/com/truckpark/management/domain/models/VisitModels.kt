package com.truckpark.management.domain.models

import java.time.Instant

enum class VisitType(val code: String, val label: String) {
    Quick("QUICK", "Quick"),
    Overnight("OVERNIGHT", "Overnight"),
    Transit("TRANSIT", "Transit"),
    Loading("LOADING", "Loading / unloading"),
    Other("OTHER", "Other");

    companion object {
        fun fromCode(code: String): VisitType = entries.firstOrNull { it.code == code } ?: Other
    }
}

enum class VisitStatus { Open, Completed }

/** Where a record stands against the server. Never claim Synced without a server confirmation. */
enum class SyncState {
    /** Saved on this handset, waiting to reach the server. */
    Pending,
    /** The server confirmed it. */
    Synced,
    /** The server found a clash (for example the truck already has an open visit). Needs a Supervisor. */
    Conflict,
    /** The server rejected it. Kept locally until retried or reviewed. */
    Failed,
}

data class TruckVisit(
    val id: String,
    /** Assigned by the server on first sync. Null until then. */
    val reference: String?,
    val plateNumber: String,
    val visitType: VisitType,
    val status: VisitStatus,
    val driverName: String?,
    val driverPhone: String?,
    val arrivalNotes: String?,
    val arrivedAt: Instant,
    val departedAt: Instant?,
    val departureNotes: String?,
    val syncState: SyncState,
    val syncError: String?,
)

data class ArrivalDraft(
    val plateNumber: String,
    val visitType: VisitType,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val notes: String? = null,
)

sealed interface ArrivalOutcome {
    /** Saved locally and queued for sync. */
    data class Saved(val visit: TruckVisit) : ArrivalOutcome

    /** The truck already has an open visit. Ask before saving a second one. */
    data class OpenVisitExists(val existing: TruckVisit) : ArrivalOutcome

    data class Invalid(val message: String) : ArrivalOutcome
}

data class SyncReport(
    val synced: Int = 0,
    val failed: Int = 0,
    val conflicts: Int = 0,
    /** True when the server could not be reached; nothing was lost, items stay pending. */
    val offline: Boolean = false,
    /** True when the sign-in was rejected and the user needs to sign in again. */
    val signedOut: Boolean = false,
)

/** Matches the server: upper case, no whitespace. */
fun normalizePlate(raw: String): String = raw.trim().uppercase().filterNot { it.isWhitespace() }

const val MAX_PLATE_LENGTH = 20

