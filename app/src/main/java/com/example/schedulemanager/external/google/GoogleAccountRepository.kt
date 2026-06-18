package com.example.schedulemanager.external.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GoogleAccountRepository {
    suspend fun userInfo(accessToken: String): GoogleAccountProfile = withContext(Dispatchers.IO) {
        val connection = (URL(USER_INFO_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GoogleCalendarApiException(
                    statusCode = code,
                    responseBody = error,
                    requestMethod = "GET",
                    requestUrl = USER_INFO_URL,
                    requestBody = null
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).let { json ->
                GoogleAccountProfile(
                    name = json.optString("name").takeIf { it.isNotBlank() },
                    email = json.optString("email").takeIf { it.isNotBlank() },
                    pictureUrl = json.optString("picture").takeIf { it.isNotBlank() }
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    }
}

data class GoogleAccountProfile(
    val name: String?,
    val email: String?,
    val pictureUrl: String?
)
