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

data class SadcCountry(
    val code: String,
    val name: String,
    val flag: String,
)

val SADC_COUNTRIES: List<SadcCountry> = listOf(
    SadcCountry("ZM", "Zambia", "\uD83C\uDDFF\uD83C\uDDF2"),
    SadcCountry("ZA", "South Africa", "\uD83C\uDDFF\uD83C\uDDE6"),
    SadcCountry("ZW", "Zimbabwe", "\uD83C\uDDFF\uD83C\uDDFC"),
    SadcCountry("CD", "DR Congo", "\uD83C\uDDE8\uD83C\uDDE9"),
    SadcCountry("TZ", "Tanzania", "\uD83C\uDDF9\uD83C\uDDFF"),
    SadcCountry("MW", "Malawi", "\uD83C\uDDF2\uD83C\uDDFC"),
    SadcCountry("BW", "Botswana", "\uD83C\uDDE7\uD83C\uDDFC"),
    SadcCountry("MZ", "Mozambique", "\uD83C\uDDF2\uD83C\uDDFF"),
    SadcCountry("NA", "Namibia", "\uD83C\uDDF3\uD83C\uDDE6"),
    SadcCountry("AO", "Angola", "\uD83C\uDDE6\uD83C\uDDF4"),
    SadcCountry("LS", "Lesotho", "\uD83C\uDDF1\uD83C\uDDF8"),
    SadcCountry("SZ", "Eswatini", "\uD83C\uDDF8\uD83C\uDDFF"),
    SadcCountry("MG", "Madagascar", "\uD83C\uDDF2\uD83C\uDDEC"),
    SadcCountry("MU", "Mauritius", "\uD83C\uDDF2\uD83C\uDDFA"),
    SadcCountry("SC", "Seychelles", "\uD83C\uDDF8\uD83C\uDDE8"),
    SadcCountry("KM", "Comoros", "\uD83C\uDDF0\uD83C\uDDF2"),
)

fun countryFlagForCode(code: String): String {
    val clean = code.trim().uppercase()
    SADC_COUNTRIES.firstOrNull { it.code == clean }?.let { return it.flag }
    if (clean.length == 2 && clean.all { it in 'A'..'Z' }) {
        val first = Character.toChars(0x1F1E6 + (clean[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (clean[1] - 'A'))
        return String(first) + String(second)
    }
    return "\uD83C\uDF0D"
}

fun countryNameForCode(code: String): String? {
    val clean = code.trim().uppercase()
    return SADC_COUNTRIES.firstOrNull { it.code == clean }?.name
}

enum class VisitFieldCategory(val code: String, val label: String) {
    Vehicle("VEHICLE", "Vehicle Identification"),
    Transporter("TRANSPORTER", "Transporter / Carrier"),
    Purpose("PURPOSE", "Visit Purpose"),
    Driver("DRIVER", "Driver & Contact"),
    Cargo("CARGO", "Cargo & Seals"),
    Notes("NOTES", "Inspection & Notes"),
    Departure("DEPARTURE", "Gate Departure");

    companion object {
        fun fromCode(code: String?): VisitFieldCategory =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: Notes
    }
}

data class CustomVisitField(
    val id: String,
    val category: VisitFieldCategory,
    val columnName: String,
    val value: String,
)

data class VisitCorrectionDraft(
    val visitId: String,
    val countryCode: String,
    val customCountryReason: String? = null,
    val frontPlate: String,
    val horsePlate: String,
    val transporter: String?,
    val visitType: VisitType,
    val customPurpose: String? = null,
    val driverName: String?,
    val driverPhone: String?,
    val arrivalNotes: String?,
    val departureNotes: String?,
    val exitVerifiedPlate: String? = null,
    val cargoDescription: String?,
    val cargoItems: List<CargoItem>,
    val seals: List<VisitSeal>,
    val customFields: List<CustomVisitField> = emptyList(),
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
    val customCountryReason: String? = null,
    val exitVerifiedPlate: String? = null,
    val arrivedByName: String? = null,
    val departedByName: String? = null,
    val customFields: List<CustomVisitField> = emptyList(),
)

fun sanitizeTagPart(raw: String): String =
    raw.replace("[", "(").replace("]", ")").replace("|", "/").trim()

fun encodeCustomFieldsTags(fields: List<CustomVisitField>): String {
    if (fields.isEmpty()) return ""
    return fields.mapNotNull { field ->
        val col = sanitizeTagPart(field.columnName)
        val v = sanitizeTagPart(field.value)
        if (col.isEmpty() || v.isEmpty()) null
        else "[Custom:${field.category.code}|$col|$v]"
    }.joinToString(" ")
}

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

fun extractPlateInfo(
    plateNumber: String,
    arrivalNotes: String?,
    departureNotes: String? = null,
): PlateInfo {
    val notes = arrivalNotes ?: ""
    val combinedNotes = buildString {
        append(notes)
        if (!departureNotes.isNullOrBlank()) {
            append(" ")
            append(departureNotes)
        }
    }
    val countryMatch = Regex("""\[Country:\s*([A-Za-z0-9]+)\]""").find(notes)
        ?: Regex("""Country:\s*([A-Za-z0-9]+)""").find(notes)
    val countryReasonMatch = Regex("""\[CountryReason:\s*([^\]]+)\]""").find(notes)
    val horseMatch = Regex("""\[Horse:\s*([^\]]+)\]""").find(notes)
        ?: Regex("""Horse(?:\s*Plate)?:\s*([^\n|]+)""").find(notes)
    val frontMatch = Regex("""\[Front:\s*([^\]]+)\]""").find(notes)
        ?: Regex("""Front(?:\s*Plate)?:\s*([^\n|]+)""").find(notes)
    val transporterMatch = Regex("""\[Transporter:\s*([^\]]+)\]""").find(notes)
        ?: Regex("""Transporter:\s*([^\n|]+)""").find(notes)
    val purposeMatch = Regex("""\[Purpose:\s*([^\]]+)\]""").find(notes)
    val exitPlateMatch = Regex("""\[ExitPlate:\s*([^\]]+)\]""").find(combinedNotes)
    val arrivedByMatch = Regex("""\[ArrivedBy:\s*([^\]]+)\]""").find(combinedNotes)
    val departedByMatch = Regex("""\[DepartedBy:\s*([^\]]+)\]""").find(combinedNotes)

    val customMatches = Regex("""\[Custom:([^\]]+)\]""").findAll(combinedNotes)
    val customFields = customMatches.mapIndexedNotNull { index, match ->
        val rawContent = match.groupValues[1]
        val parts = rawContent.split("|")
        when {
            parts.size >= 3 -> {
                val category = VisitFieldCategory.fromCode(parts[0])
                val col = parts[1].trim()
                val v = parts.subList(2, parts.size).joinToString("|").trim()
                if (col.isNotEmpty() && v.isNotEmpty()) {
                    CustomVisitField(
                        id = "custom_${index}_${col.hashCode()}",
                        category = category,
                        columnName = col,
                        value = v,
                    )
                } else null
            }
            parts.size == 2 -> {
                val col = parts[0].trim()
                val v = parts[1].trim()
                if (col.isNotEmpty() && v.isNotEmpty()) {
                    CustomVisitField(
                        id = "custom_${index}_${col.hashCode()}",
                        category = VisitFieldCategory.Notes,
                        columnName = col,
                        value = v,
                    )
                } else null
            }
            else -> null
        }
    }.distinctBy { "${it.category.code}|${it.columnName.lowercase()}|${it.value}" }.toList()

    val country = countryMatch?.groupValues?.get(1)?.trim()?.uppercase() ?: "ZM"
    val countryReason = countryReasonMatch?.groupValues?.get(1)?.trim()
    val front = frontMatch?.groupValues?.get(1)?.trim() ?: plateNumber
    val horse = horseMatch?.groupValues?.get(1)?.trim() ?: plateNumber
    val transporter = transporterMatch?.groupValues?.get(1)?.trim()
    val purpose = purposeMatch?.groupValues?.get(1)?.trim()
    val exitVerifiedPlate = exitPlateMatch?.groupValues?.get(1)?.trim()
    val arrivedByName = arrivedByMatch?.groupValues?.get(1)?.trim()
    val departedByName = departedByMatch?.groupValues?.get(1)?.trim()

    return PlateInfo(
        country = country,
        frontPlate = front,
        horsePlate = horse,
        transporter = transporter,
        customPurpose = purpose,
        customCountryReason = countryReason,
        exitVerifiedPlate = exitVerifiedPlate,
        arrivedByName = arrivedByName,
        departedByName = departedByName,
        customFields = customFields,
    )
}

fun cleanDisplayNotes(rawNotes: String?): String? {
    if (rawNotes.isNullOrBlank()) return null
    val stripped = rawNotes
        .replace(Regex("""\[Country:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[CountryReason:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Horse:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Front:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Transporter:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Purpose:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[ExitPlate:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[ArrivedBy:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[DepartedBy:\s*[^\]]+\]\s*"""), "")
        .replace(Regex("""\[Custom:[^\]]+\]\s*"""), "")
        .replace(Regex("""Country:\s*[A-Za-z0-9]+\s*\|?\s*"""), "")
        .replace(Regex("""Horse(?:\s*Plate)?:\s*[^\n|]+\s*\|?\s*"""), "")
        .replace(Regex("""Front(?:\s*Plate)?:\s*[^\n|]+\s*\|?\s*"""), "")
        .trim()
    return stripped.ifBlank { null }
}

