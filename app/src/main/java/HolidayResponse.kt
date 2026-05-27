package com.example.schedulemanager

import com.google.gson.annotations.SerializedName

data class HolidayResponse(
    @SerializedName("response") val response: Response
)

data class Response(
    @SerializedName("body") val body: Body
)

data class Body(
    @SerializedName("items") val items: Items?,
    @SerializedName("totalCount") val totalCount: Int
)

data class Items(
    @SerializedName("item") val item: List<HolidayItem>?
)

data class HolidayItem(
    @SerializedName("dateName") val dateName: String, // 공휴일 이름 (예: 설날)
    @SerializedName("isHoliday") val isHoliday: String, // 공휴일 여부 (Y/N)
    @SerializedName("locdate") val locdate: Int // 날짜 (예: 20260101)
)