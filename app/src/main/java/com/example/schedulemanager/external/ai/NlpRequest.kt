

package com.example.schedulemanager.external.ai

import com.google.gson.annotations.SerializedName

/**
 * 외부 AI 백엔드 서버의 JSON 응답 데이터를 파싱하기 위한 데이터 모델 클래스 (DTO)
 * 백엔드의 Snake Case 필드명과 안드로이드의 Camel Case 변수명을 매핑합니다.
 */
data class NlpResponse(
    @SerializedName("title")
    val title: String?,

    @SerializedName("scheduled_date")
    val scheduledDate: Long?,

    @SerializedName("start_time_minutes")
    val startTimeMinutes: Int?,

    @SerializedName("duration_minutes")
    val durationMinutes: Int?
)
