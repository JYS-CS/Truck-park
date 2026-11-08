package com.truckpark.management.data.remote.visits

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.remote.HttpRequest
import com.truckpark.management.data.remote.HttpResponse
import com.truckpark.management.data.remote.HttpTransport
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdsVisitsRemoteDataSourceTest {

    private class FakeTransport(private val reply: () -> HttpResponse) : HttpTransport {
        var last: HttpRequest? = null
        override suspend fun execute(request: HttpRequest): HttpResponse {
            last = request
            return reply()
        }
    }

    private val visitJson = """
        {"visit":{"id":"v1","reference":"TP-2026-000001","vehicle_id":"x","plate_number":"ABC123",
        "visit_type":"QUICK","status":"OPEN","driver_name":null,"arrived_at":"2026-11-08T09:00:00.000Z",
        "departed_at":null,"row_version":1},"request_id":"r"}
    """.trimIndent()

    private val payload = ArrivalPayload(
        visitId = "v1", plateNumber = "ABC123", visitType = "QUICK", driverName = null,
        driverPhone = null, notes = null, arrivedAt = Instant.parse("2026-11-08T09:00:00Z"),
        allowDuplicate = false,
    )

    @Test
    fun arriveParsesVisitAndSendsBearerAndBody() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, visitJson) }
        val remote = OrdsVisitsRemoteDataSource("https://h/ords/truckpark/v1/", transport)

        val result = remote.arrive("tok", payload)

        val visit = (result as AuthResult.Success).value
        assertEquals("TP-2026-000001", visit.reference)
        assertNull(visit.driverName)
        assertNull(visit.departedAt)
        assertEquals("https://h/ords/truckpark/v1/visits/arrive", transport.last?.url)
        assertEquals("Bearer tok", transport.last?.headers?.get("Authorization"))
        assertTrue(transport.last?.body.orEmpty().contains("\"allow_duplicate\":\"N\""))
    }

    @Test
    fun openVisitConflictKeepsServerCode() = runBlocking {
        val body = """{"code":"OPEN_VISIT_EXISTS","message":"Truck ABC123 already has an open visit.","request_id":"r"}"""
        val remote = OrdsVisitsRemoteDataSource("https://h/", FakeTransport { HttpResponse(409, body) })

        val error = (remote.arrive("tok", payload) as AuthResult.Failure).error

        assertEquals("OPEN_VISIT_EXISTS", error.code)
        assertEquals(409, error.httpStatus)
    }

    @Test
    fun unreachableServerIsReportedAsNetworkError() = runBlocking {
        val remote = OrdsVisitsRemoteDataSource("https://h/", FakeTransport { throw IOException("down") })

        val error = (remote.list("tok") as AuthResult.Failure).error

        assertEquals(AuthError.NETWORK, error.code)
    }

    @Test
    fun listReadsEveryVisit() = runBlocking {
        val body = """{"visits":[${visitJson.substringAfter("\"visit\":").substringBeforeLast(",\"request_id\"")}]}"""
        val remote = OrdsVisitsRemoteDataSource("https://h/", FakeTransport { HttpResponse(200, body) })

        val visits = (remote.list("tok") as AuthResult.Success).value

        assertEquals(1, visits.size)
        assertEquals("ABC123", visits[0].plateNumber)
    }
}

