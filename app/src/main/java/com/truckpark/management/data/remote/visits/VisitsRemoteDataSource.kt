package com.truckpark.management.data.remote.visits

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.remote.HttpRequest
import com.truckpark.management.data.remote.HttpResponse
import com.truckpark.management.data.remote.HttpTransport
import com.truckpark.management.domain.models.CargoItem
import com.truckpark.management.domain.models.VisitCorrectionRecord
import com.truckpark.management.domain.models.VisitSeal
import com.truckpark.management.domain.models.decodeCargoItemsJson
import com.truckpark.management.domain.models.decodeSealsJson
import com.truckpark.management.domain.models.encodeCargoItemsJson
import com.truckpark.management.domain.models.encodeSealsJson
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

/** A visit exactly as the server reports it. */
data class RemoteVisit(
    val id: String,
    val reference: String,
    val plateNumber: String,
    val visitType: String,
    val status: String,
    val driverName: String?,
    val driverPhone: String?,
    val arrivalNotes: String?,
    val arrivedAt: Instant,
    val departedAt: Instant?,
    val departureNotes: String?,
    val rowVersion: Long,
    val countryCode: String? = null,
    val frontPlate: String? = null,
    val horsePlate: String? = null,
    val transporter: String? = null,
    val cargoDescription: String? = null,
    val cargoItems: List<CargoItem> = emptyList(),
    val seals: List<VisitSeal> = emptyList(),
)

data class ArrivalPayload(
    val visitId: String,
    val plateNumber: String,
    val visitType: String,
    val driverName: String?,
    val driverPhone: String?,
    val notes: String?,
    val arrivedAt: Instant,
    val allowDuplicate: Boolean,
    val countryCode: String? = null,
    val frontPlate: String? = null,
    val horsePlate: String? = null,
    val transporter: String? = null,
    val cargoDescription: String? = null,
    val cargoItems: List<CargoItem> = emptyList(),
    val seals: List<VisitSeal> = emptyList(),
)

data class CorrectionPayload(
    val visitId: String,
    val plateNumber: String,
    val countryCode: String,
    val frontPlate: String,
    val horsePlate: String,
    val transporter: String?,
    val visitType: String,
    val driverName: String?,
    val driverPhone: String?,
    val arrivalNotes: String?,
    val departureNotes: String?,
    val cargoDescription: String?,
    val cargoItems: List<CargoItem>,
    val seals: List<VisitSeal>,
    val reason: String?,
    val expectedRowVersion: Long?,
)

interface VisitsRemoteDataSource {
    suspend fun arrive(sessionToken: String, payload: ArrivalPayload): AuthResult<RemoteVisit>
    suspend fun depart(sessionToken: String, visitId: String, departedAt: Instant, notes: String?): AuthResult<RemoteVisit>
    suspend fun correct(sessionToken: String, payload: CorrectionPayload): AuthResult<RemoteVisit>
    suspend fun listHistory(sessionToken: String, visitId: String): AuthResult<List<VisitCorrectionRecord>>
    suspend fun list(sessionToken: String): AuthResult<List<RemoteVisit>>
}

/** Oracle ORDS implementation. Endpoints match backend/ords/02_ords_module.sql. */
class OrdsVisitsRemoteDataSource(
    baseUrl: String,
    private val transport: HttpTransport,
) : VisitsRemoteDataSource {

    private val base = baseUrl.trimEnd('/') + "/"

    override suspend fun arrive(sessionToken: String, payload: ArrivalPayload): AuthResult<RemoteVisit> = call(
        path = "visits/arrive",
        body = JSONObject().apply {
            put("visit_id", payload.visitId)
            put("plate_number", payload.plateNumber)
            put("visit_type", payload.visitType)
            payload.driverName?.let { put("driver_name", it) }
            payload.driverPhone?.let { put("driver_phone", it) }
            payload.notes?.let { put("notes", it) }
            put("arrived_at", payload.arrivedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
            put("allow_duplicate", if (payload.allowDuplicate) "Y" else "N")
            payload.countryCode?.let { put("country_code", it) }
            payload.frontPlate?.let { put("front_plate", it) }
            payload.horsePlate?.let { put("horse_plate", it) }
            payload.transporter?.let { put("transporter", it) }
            payload.cargoDescription?.let { put("cargo_description", it) }
            encodeCargoItemsJson(payload.cargoItems)?.let { put("cargo_items_json", it) }
            encodeSealsJson(payload.seals)?.let { put("seals_json", it) }
        },
        bearer = sessionToken,
    ) { json -> parseVisit(json.getJSONObject("visit")) }

    override suspend fun depart(
        sessionToken: String,
        visitId: String,
        departedAt: Instant,
        notes: String?,
    ): AuthResult<RemoteVisit> = call(
        path = "visits/${visitId.trim()}/depart",
        body = JSONObject().apply {
            put("departed_at", departedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
            notes?.let { put("notes", it) }
        },
        bearer = sessionToken,
    ) { json -> parseVisit(json.getJSONObject("visit")) }

    override suspend fun correct(sessionToken: String, payload: CorrectionPayload): AuthResult<RemoteVisit> = call(
        path = "visits/${payload.visitId.trim()}/correct",
        body = JSONObject().apply {
            put("plate_number", payload.plateNumber)
            put("country_code", payload.countryCode)
            put("front_plate", payload.frontPlate)
            put("horse_plate", payload.horsePlate)
            payload.transporter?.let { put("transporter", it) }
            put("visit_type", payload.visitType)
            payload.driverName?.let { put("driver_name", it) }
            payload.driverPhone?.let { put("driver_phone", it) }
            payload.arrivalNotes?.let { put("arrival_notes", it) }
            payload.departureNotes?.let { put("departure_notes", it) }
            payload.cargoDescription?.let { put("cargo_description", it) }
            encodeCargoItemsJson(payload.cargoItems)?.let { put("cargo_items_json", it) }
            encodeSealsJson(payload.seals)?.let { put("seals_json", it) }
            payload.reason?.let { put("reason", it) }
            payload.expectedRowVersion?.let { put("expected_row_version", it) }
        },
        bearer = sessionToken,
    ) { json -> parseVisit(json.getJSONObject("visit")) }

    override suspend fun listHistory(
        sessionToken: String,
        visitId: String,
    ): AuthResult<List<VisitCorrectionRecord>> = call(
        method = "GET",
        path = "visits/${visitId.trim()}/history",
        bearer = sessionToken,
    ) { json ->
        val items = json.optJSONArray("history")
        List(items?.length() ?: 0) { i ->
            val obj = items!!.getJSONObject(i)
            VisitCorrectionRecord(
                id = obj.getString("id"),
                visitId = obj.optString("visit_id").ifBlank { visitId },
                editedByName = obj.optNullableString("edited_by_name"),
                editedAt = Instant.parse(obj.getString("edited_at")),
                reason = obj.optNullableString("reason"),
                changesSummary = obj.optString("changes_summary").ifBlank { "Visit updated" },
                wasCritical = obj.optString("was_critical").equals("Y", ignoreCase = true),
            )
        }
    }

    override suspend fun list(sessionToken: String): AuthResult<List<RemoteVisit>> = call(
        method = "GET",
        path = "visits",
        bearer = sessionToken,
    ) { json ->
        val items = json.optJSONArray("visits")
        List(items?.length() ?: 0) { i -> parseVisit(items!!.getJSONObject(i)) }
    }

    // ------------------------------------------------------------------ plumbing

    private suspend fun <T> call(
        method: String = "POST",
        path: String,
        body: JSONObject? = null,
        bearer: String,
        parse: (JSONObject) -> T,
    ): AuthResult<T> {
        val headers = mapOf("Authorization" to "Bearer $bearer")
        val response = try {
            transport.execute(HttpRequest(method, base + path, headers, body?.toString()))
        } catch (e: IOException) {
            return AuthResult.Failure(
                AuthError(AuthError.NETWORK, "Cannot reach the server. Check your connection and try again."),
            )
        }

        if (response.status !in 200..299) return AuthResult.Failure(errorFrom(response))

        return try {
            val json = if (response.body.isBlank()) JSONObject() else JSONObject(response.body)
            AuthResult.Success(parse(json))
        } catch (e: JSONException) {
            badResponse(response)
        } catch (e: DateTimeParseException) {
            badResponse(response)
        }
    }

    private fun badResponse(response: HttpResponse): AuthResult.Failure = AuthResult.Failure(
        AuthError(AuthError.BAD_RESPONSE, "The server sent something unexpected.", response.status),
    )

    /** Reads the common error shape: { code, message, request_id }. Never trusts the body to be JSON. */
    private fun errorFrom(response: HttpResponse): AuthError {
        val json = try { JSONObject(response.body) } catch (e: JSONException) { null }
        return AuthError(
            code = json?.optString("code").orEmpty().ifBlank { "HTTP_${response.status}" },
            message = json?.optString("message").orEmpty().ifBlank { "Request failed (${response.status})." },
            httpStatus = response.status,
            requestId = json?.optString("request_id")?.ifBlank { null },
        )
    }

    private fun parseVisit(json: JSONObject): RemoteVisit {
        val rawCargo = json.optNullableString("cargo_items_json")
            ?: json.optJSONArray("cargo_items")?.toString()
        val rawSeals = json.optNullableString("seals_json")
            ?: json.optJSONArray("seals")?.toString()
        return RemoteVisit(
            id = json.getString("id"),
            reference = json.getString("reference"),
            plateNumber = json.getString("plate_number"),
            visitType = json.getString("visit_type"),
            status = json.getString("status"),
            driverName = json.optNullableString("driver_name"),
            driverPhone = json.optNullableString("driver_phone"),
            arrivalNotes = json.optNullableString("arrival_notes"),
            arrivedAt = Instant.parse(json.getString("arrived_at")),
            departedAt = json.optNullableString("departed_at")?.let { Instant.parse(it) },
            departureNotes = json.optNullableString("departure_notes"),
            rowVersion = json.optLong("row_version", 1L),
            countryCode = json.optNullableString("country_code"),
            frontPlate = json.optNullableString("front_plate"),
            horsePlate = json.optNullableString("horse_plate"),
            transporter = json.optNullableString("transporter"),
            cargoDescription = json.optNullableString("cargo_description"),
            cargoItems = decodeCargoItemsJson(rawCargo),
            seals = decodeSealsJson(rawSeals),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        val str = optString(key)
        return if (str.isBlank() || str.equals("null", ignoreCase = true)) null else str
    }
}

