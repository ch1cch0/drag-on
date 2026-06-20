package com.example.schedulemanager.external.ai

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit


data class NlpRequest(
    val text: String
)


interface AiApiService {
    @POST("nlp-analyze")
    suspend fun parseSchedule(@Body request: NlpRequest): NlpResponse

    companion object {
        fun create(baseUrl: String): AiApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AiApiService::class.java)
        }
    }
}
