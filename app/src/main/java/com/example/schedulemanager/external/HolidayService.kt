package com.example.schedulemanager.external

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface HolidayService {
    @GET("B090041/openapi/service/SpcdeInfoService/getHoliDeInfo")
    fun getHolidays(
        // ◀ value = "serviceKey" 형태로 이름을 명확히 지정해 줍니다.
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("solYear") solYear: Int,
        @Query("solMonth") solMonth: String,
        @Query("_type") type: String = "json"
    ): Call<HolidayResponse>
}