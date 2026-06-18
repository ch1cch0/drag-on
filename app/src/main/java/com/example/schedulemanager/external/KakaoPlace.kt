package com.example.schedulemanager.external

data class KakaoPlace(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int?
) {
    val metaText: String
        get() {
            val parts = listOfNotNull(address, distanceMeters?.let { formatDistance(it) })
            return parts.ifEmpty { listOf("No address") }.joinToString(" · ")
        }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) {
            "%.1f km".format(meters / 1000f)
        } else {
            "$meters m"
        }
    }
}
