package com.truckpark.management.domain.models

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

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
    /** The server found a clash (for example the truck already has an open visit or version mismatch). Needs a Supervisor. */
    Conflict,
    /** The server rejected it. Kept locally until retried or reviewed. */
    Failed,
}

enum class SealCondition(val code: String, val label: String) {
    Intact("INTACT", "Intact"),
    Broken("BROKEN", "Broken"),
    Missing("MISSING", "Missing"),
    Tampered("TAMPERED", "Tampered");

    companion object {
        fun fromCode(code: String?): SealCondition =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: Intact
    }
}

data class CargoItem(
    val id: String,
    val description: String,
    val quantity: String? = null,
    val unit: String? = null,
    val notes: String? = null,
)

data class VisitSeal(
    val id: String,
    val sealNumber: String,
    val sealType: String = "Bolt",
    val condition: SealCondition = SealCondition.Intact,
    val notes: String? = null,
    val localPhotoPath: String? = null,
)

data class VisitCorrectionRecord(
    val id: String,
    val visitId: String,
    val editedByName: String?,
    val editedAt: Instant,
    val reason: String?,
    val changesSummary: String,
    val wasCritical: Boolean,
)

data class VisitCorrectionDraft(
    val visitId: String,
    val countryCode: String,
    val frontPlate: String,
    val horsePlate: String,
    val transporter: String?,
    val visitType: VisitType,
    val driverName: String?,
    val driverPhone: String?,
    val arrivalNotes: String?,
    val departureNotes: String?,
    val cargoDescription: String?,
    val cargoItems: List<CargoItem>,
    val seals: List<VisitSeal>,
    val reason: String?,
)

enum class SyncAlertSeverity { Info, Warning, Error }

data class SyncAlertBanner(
    val message: String,
    val severity: SyncAlertSeverity,
    val showRetry: Boolean,
    val requiresSignIn: Boolean = false,
    val needsConflictReview: Boolean = false,
)

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
    val cargoDescription: String? = null,
    val cargoItems: List<CargoItem> = emptyList(),
    val seals: List<VisitSeal> = emptyList(),
    val conflictServerSnapshot: String? = null,
    val isEdited: Boolean = false,
    val rowVersion: Long = 1L,
)

data class ArrivalDraft(
    val plateNumber: String,
    val visitType: VisitType,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val notes: String? = null,
    val cargoDescription: String? = null,
    val cargoItems: List<CargoItem> = emptyList(),
    val seals: List<VisitSeal> = emptyList(),
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
    val lastErrorMessage: String? = null,
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

fun encodeCargoItemsJson(items: List<CargoItem>): String? {
    if (items.isEmpty()) return null
    val array = JSONArray()
    for (item in items) {
        array.put(
            JSONObject().apply {
                put("id", item.id)
                put("description", item.description)
                item.quantity?.takeIf { it.isNotBlank() }?.let { put("quantity", it) }
                item.unit?.takeIf { it.isNotBlank() }?.let { put("unit", it) }
                item.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
            },
        )
    }
    return array.toString()
}

fun decodeCargoItemsJson(raw: String?): List<CargoItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val desc = obj.optString("description").trim()
                if (desc.isEmpty()) continue
                add(
                    CargoItem(
                        id = obj.optString("id").ifBlank { "cargo_$i" },
                        description = desc,
                        quantity = obj.optString("quantity").takeIf { it.isNotBlank() && it != "null" },
                        unit = obj.optString("unit").takeIf { it.isNotBlank() && it != "null" },
                        notes = obj.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun encodeSealsJson(seals: List<VisitSeal>): String? {
    if (seals.isEmpty()) return null
    val array = JSONArray()
    for (seal in seals) {
        array.put(
            JSONObject().apply {
                put("id", seal.id)
                put("seal_number", seal.sealNumber)
                put("seal_type", seal.sealType)
                put("condition", seal.condition.code)
                seal.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
                seal.localPhotoPath?.takeIf { it.isNotBlank() }?.let { put("local_photo_path", it) }
            },
        )
    }
    return array.toString()
}

fun decodeSealsJson(raw: String?): List<VisitSeal> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val num = obj.optString("seal_number").trim()
                if (num.isEmpty()) continue
                add(
                    VisitSeal(
                        id = obj.optString("id").ifBlank { "seal_$i" },
                        sealNumber = num,
                        sealType = obj.optString("seal_type").ifBlank { "Bolt" },
                        condition = SealCondition.fromCode(obj.optString("condition")),
                        notes = obj.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                        localPhotoPath = obj.optString("local_photo_path").takeIf { it.isNotBlank() && it != "null" },
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

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

