package com.example.schedulemanager.external.google

import com.example.schedulemanager.data.ScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GoogleCalendarRepository {
    suspend fun upsertEvent(accessToken: String, schedule: ScheduleEntity): String = withContext(Dispatchers.IO) {
        val eventId = schedule.googleEventId
        val url = if (eventId == null) {
            URL("https://www.googleapis.com/calendar/v3/calendars/primary/events")
        } else {
            URL("https://www.googleapis.com/calendar/v3/calendars/primary/events/${urlEncode(eventId)}")
        }
        val method = if (eventId == null) "POST" else "PUT"
        val body = GoogleCalendarMapper.toEventJson(schedule).toString()
        val response = request(url, method, accessToken, body)
        JSONObject(response).getString("id")
    }

    suspend fun deleteEvent(accessToken: String, eventId: String) = withContext(Dispatchers.IO) {
        request(
            url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events/${urlEncode(eventId)}"),
            method = "DELETE",
            accessToken = accessToken,
            body = null,
            allowEmptyResponse = true
        )
    }

    private fun request(
        url: URL,
        method: String,
        accessToken: String,
        body: String?,
        allowEmptyResponse: Boolean = false
    ): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code in 200..299) {
                if (allowEmptyResponse || code == HttpURLConnection.HTTP_NO_CONTENT) return ""
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleCalendarApiException(
                statusCode = code,
                responseBody = error,
                requestMethod = method,
                requestUrl = url.toString(),
                requestBody = body
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}

class GoogleCalendarApiException(
    val statusCode: Int,
    val responseBody: String,
    val requestMethod: String,
    val requestUrl: String,
    val requestBody: String?
) : IllegalStateException("Google Calendar API failed: $statusCode $responseBody")
