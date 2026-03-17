package com.vidalabs.budget.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

@Serializable
data class SyncPayload(val events: List<SyncEvent>)

class HttpSyncClient {
    private val connectTimeoutMs = 10_000
    private val readTimeoutMs = 30_000

    /**
     * GET all sync events from the endpoint.
     * Expects the server to return JSON: {"events": [...]}
     */
    fun getEvents(endpointUrl: String): List<SyncEvent> {
        val connection = openConnection(endpointUrl)
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.take(200).orEmpty()
            throw Exception("GET failed with HTTP $responseCode: $errorBody".trimEnd())
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return SyncJson.decodeFromString<SyncPayload>(body).events
    }

    /**
     * POST sync events to the endpoint.
     * Sends JSON: {"events": [...]}
     */
    fun postEvents(endpointUrl: String, events: List<SyncEvent>) {
        if (events.isEmpty()) return

        val connection = openConnection(endpointUrl)
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs

        val body = SyncJson.encodeToString(SyncPayload(events))
        connection.outputStream.bufferedWriter().use { it.write(body) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.take(200).orEmpty()
            throw Exception("POST failed with HTTP $responseCode: $errorBody".trimEnd())
        }
    }

    private fun openConnection(endpointUrl: String): HttpURLConnection {
        val url = try {
            URL(endpointUrl)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Invalid endpoint URL: $endpointUrl", e)
        }
        val protocol = url.protocol.lowercase()
        if (protocol != "http" && protocol != "https") {
            throw IllegalArgumentException("Endpoint URL must use http or https (got: $protocol)")
        }
        return url.openConnection() as HttpURLConnection
    }
}
