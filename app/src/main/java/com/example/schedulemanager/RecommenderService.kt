package com.example.schedulemanager

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// ==========================================
// 1. 드래그 앤 드롭 최적 시간대 예측용 DTO
// ==========================================
data class PredictRequest(
    val categoryId: Long,
    val durationMinutes: Int,
    val titleLength: Int
)

data class PredictResponse(
    val recommended_day: Int,
    val recommended_time_slot: Int,
    val is_ai: Boolean
)

// ==========================================
// 2. ✨ 인박스 한 줄 스마트 입력(NLP) 전용 DTO
// ==========================================
// ◀ 파이썬 FastAPI 서버 규격에 맞게 데이터를 안전하게 캡슐화하는 요청 상자입니다.
data class NlpRequest(
    val text: String
)

// ◀ 파이썬 서버가 자연어를 분석하고 머신러닝 스코어를 결합해 던져줄 응답 구조입니다.
data class NlpResponse(
    val parsedTitle: String,       // AI가 요약 가공한 순수 제목 (예: "팀플 회의")
    val parsedDate: String,        // AI가 추출한 날짜 기준 키워드 (예: "내일")
    val parsedCategory: String,    // AI가 판단한 카테고리 명칭 (예: "회의")
    val recommendedTimeSlot: Int   // AI 추천 타임슬롯 분 환산값 (예: 840 -> 14:00)
)

// ==========================================
// 3. Retrofit 인터페이스 네트워크 명세 정의
// ==========================================
interface RecommenderService {

    // 기존 타임테이블 드래그 추천 API
    @POST("predict")
    fun getBestTimeSlot(
        @Body request: PredictRequest
    ): Call<PredictResponse>

    // 인박스 자연어 문장 분석 및 자동완성 AI API
    @POST("nlp-analyze")
    fun nlpAnalyze(
        @Body request: NlpRequest
    ): Call<NlpResponse>
}