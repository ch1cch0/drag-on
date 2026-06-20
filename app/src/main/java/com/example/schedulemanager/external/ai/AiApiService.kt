package com.example.schedulemanager.external.ai

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// 1. 서버가 수신 대기하는 키값 "text"
data class NlpRequest(
    val text: String
)

// ❌ 기존에 여기에 중복 선언되어 있던 NlpResponse 클래스는 삭제했습니다.
// 💡 이제 별도 파일로 분리한 @SerializedName이 포함된 NlpResponse 객체를 자동으로 참조하게 됩니다.

interface AiApiService {
    // 2. Swagger Docs에서 확인한 진짜 주소 매핑
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
