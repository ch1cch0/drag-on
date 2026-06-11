package com.example.schedulemanager.external

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate

class HolidayRepository(
    private val apiKey: String
) {
    private val service: HolidayService = Retrofit.Builder()
        .baseUrl("https://apis.data.go.kr/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HolidayService::class.java)

    suspend fun holidaysForMonth(month: LocalDate): List<Holiday> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val response = service.getHolidays(
            serviceKey = apiKey,
            solYear = month.year,
            solMonth = "%02d".format(month.monthValue)
        ).execute()
        if (!response.isSuccessful) error("Holiday API failed: ${response.code()}")
        response.body()
            ?.response
            ?.body
            ?.items
            ?.item
            .orEmpty()
            .filter { it.isHoliday == "Y" }
            .mapNotNull { it.toHolidayOrNull() }
    }

    private fun HolidayItem.toHolidayOrNull(): Holiday? {
        val raw = locdate.toString()
        if (raw.length != 8) return null
        return runCatching {
            Holiday(
                date = LocalDate.of(raw.substring(0, 4).toInt(), raw.substring(4, 6).toInt(), raw.substring(6, 8).toInt()),
                name = dateName
            )
        }.getOrNull()
    }
}
