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

data class PlateInfo(
    val country: String,
    val frontPlate: String,
    val horsePlate: String,
    val transporter: String? = null,
    val customPurpose: String? = null,
)

fun extractPlateInfo(plateNumber: String, arrivalNotes: String?): PlateInfo {
    val notes = arrivalNotes ?: ""
    val countryMatch = Regex("""\[Country:\s*([A-Za-z0-9]+)\]""").find(notes)
        ?: Regex("""Country:\s*([A-Za-z0-9]+)""").find(notes)
    val horseMatch = Regex("""\[Horse:\s*([^\]]+)\]""").find(notes)
        ?: Regex("""Horse(?:\s*Plate)?:\s*([^\n|]+)""").find(notes)
    val frontMatch = Regex("""\[Front:\s*([^\]]+)\]""").find(notes)
        ?: Regex("""Front(?:\s*Plate)?:\s*([^\n|]+)""").find(notes)
    val transporterMatch = Regex("""\[Transporter:\s*([^\]]+)\]""").find(notes)
        ?: Regex("""Transporter:\s*([^\n|]+)""").find(notes)
    val purposeMatch = Regex("""\[Purpose:\s*([^\]]+)\]""").find(notes)

    val country = countryMatch?.groupValues?.get(1)?.trim()?.uppercase() ?: "ZM"
    val front = frontMatch?.groupValues?.get(1)?.trim() ?: plateNumber
    val horse = horseMatch?.groupValues?.get(1)?.trim() ?: plateNumber
    val transporter = transporterMatch?.groupValues?.get(1)?.trim()
    val purpose = purposeMatch?.groupValues?.get(1)?.trim()

    return PlateInfo(country = country, frontPlate = front, horsePlate = horse, transporter = transporter, customPurpose = purpose)
}

fun cleanDisplayNotes(rawNotes: String?): String? {
    if (rawNotes.isNullOrBlank()) return null
    val stripped = rawNotes
        .replace(Regex("""\[Country:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Horse:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Front:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Transporter:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Purpose:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""Country:\s*[A-Za-z0-9]+\s*\|?\s*"""), "")
        .replace(Regex("""Horse(?:\s*Plate)?:\s*[^\n|]+\s*\|?\s*"""), "")
        .replace(Regex("""Front(?:\s*Plate)?:\s*[^\n|]+\s*\|?\s*"""), "")
        .trim()
    return stripped.ifBlank { null }
}

