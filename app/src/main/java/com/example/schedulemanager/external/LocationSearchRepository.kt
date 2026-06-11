package com.example.schedulemanager.external

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class LocationSearchRepository(
    private val kakaoRestApiKey: String
) {
    suspend fun search(query: String, location: Location?): List<KakaoPlace> = withContext(Dispatchers.IO) {
        if (kakaoRestApiKey.isBlank()) error("Kakao REST API key is missing.")
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val locationQuery = location?.let {
            "&x=${it.longitude}&y=${it.latitude}&radius=20000&sort=distance"
        }.orEmpty()
        val url = URL("https://dapi.kakao.com/v2/local/search/keyword.json?query=$encodedQuery&size=15$locationQuery")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Authorization", "KakaoAK $kakaoRestApiKey")
        }
        try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error("Kakao Local API failed: ${connection.responseCode}")
            val documents = JSONObject(body).getJSONArray("documents")
            buildList {
                for (index in 0 until documents.length()) {
                    val item = documents.getJSONObject(index)
                    val longitude = item.optString("x").toDoubleOrNull()
                    val latitude = item.optString("y").toDoubleOrNull()
                    if (latitude != null && longitude != null) {
                        val roadAddress = item.optString("road_address_name").takeIf { it.isNotBlank() }
                        add(
                            KakaoPlace(
                                name = item.getString("place_name"),
                                address = roadAddress ?: item.optString("address_name").takeIf { it.isNotBlank() },
                                latitude = latitude,
                                longitude = longitude,
                                distanceMeters = item.optString("distance").toIntOrNull()
                            )
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
