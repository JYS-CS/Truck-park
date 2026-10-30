package com.truckpark.management.data.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponse(val status: Int, val body: String)

/**
 * The only seam to the network. Tests use a fake; production uses [HttpUrlConnectionTransport].
 * Implementations throw [IOException] when the server cannot be reached.
 */
interface HttpTransport {
    @Throws(IOException::class)
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** Plain platform HTTP, no extra library. HTTPS only unless explicitly relaxed for a local server. */
class HttpUrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxResponseBytes: Int = 1_000_000,
    private val allowCleartext: Boolean = false,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        if (!allowCleartext && !request.url.startsWith("https://", ignoreCase = true)) {
            throw IOException("Refusing non-HTTPS request")
        }
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            if (request.body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            HttpResponse(status, stream?.let { readCapped(it) }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(stream: java.io.InputStream): String = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > maxResponseBytes) throw IOException("Response too large")
        }
        out.toString(Charsets.UTF_8.name())
    }
}
