package psiolent.caliper.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val status: Int, val body: String) : Exception("HTTP $status: ${body.take(600)}")

/**
 * Minimal HTTP over HttpURLConnection. Caliper makes only a handful of calls
 * (OpenRouter, USDA), so a third-party HTTP client would be surface without payoff.
 */
object Http {

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        request("GET", url, headers, null)

    suspend fun postJson(url: String, headers: Map<String, String>, body: String): String =
        request("POST", url, headers + ("Content-Type" to "application/json"), body)

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 180_000
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (status !in 200..299) throw HttpException(status, text)
            text
        } finally {
            connection.disconnect()
        }
    }
}
