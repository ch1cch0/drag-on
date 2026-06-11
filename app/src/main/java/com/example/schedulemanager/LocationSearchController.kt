package com.example.schedulemanager

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationSearchController(
    private val activity: AppCompatActivity,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val locationClient: FusedLocationProviderClient,
    private val locationSearchRepository: LocationSearchRepository,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>
) {
    private var pendingSearch: PendingSearch? = null

    fun search(query: String, onSelected: (KakaoPlace) -> Unit) {
        if (query.isBlank()) {
            Toast.makeText(activity, "Enter a location first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) {
            Toast.makeText(activity, "Kakao REST API key is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        if (hasLocationPermission()) {
            searchWithCurrentPosition(query, onSelected)
            return
        }
        pendingSearch = PendingSearch(query, onSelected)
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun onPermissionResult(permissions: Map<String, Boolean>) {
        val pending = pendingSearch ?: return
        pendingSearch = null
        if (permissions.values.any { it }) {
            searchWithCurrentPosition(pending.query, pending.onSelected)
        } else {
            Toast.makeText(activity, "Searching without current location.", Toast.LENGTH_SHORT).show()
            searchLocations(pending.query, pending.onSelected, null)
        }
    }

    private fun searchWithCurrentPosition(query: String, onSelected: (KakaoPlace) -> Unit) {
        lifecycleScope.launch {
            val location = currentLocationOrNull()
            if (location == null) {
                Toast.makeText(activity, "Searching without current location.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Searching near current location.", Toast.LENGTH_SHORT).show()
            }
            searchLocations(query, onSelected, location)
        }
    }

    private fun searchLocations(query: String, onSelected: (KakaoPlace) -> Unit, location: Location?) {
        lifecycleScope.launch {
            val result = runCatching { locationSearchRepository.search(query, location) }
            val places = result.getOrNull().orEmpty()
            if (result.isFailure) {
                Toast.makeText(activity, "Location search failed.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (places.isEmpty()) {
                Toast.makeText(activity, "No locations found.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            LocationSearchDialog(activity, places, onSelected).show()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun currentLocationOrNull(): Location? {
        if (!hasLocationPermission()) {
            return null
        }
        return withTimeoutOrNull(1200) {
            cachedOrCurrentLocation()
        }
    }

    private suspend fun cachedOrCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            locationClient.lastLocation
                .addOnSuccessListener { cachedLocation ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (cachedLocation != null) {
                        continuation.resume(cachedLocation)
                    } else {
                        val tokenSource = CancellationTokenSource()
                        continuation.invokeOnCancellation { tokenSource.cancel() }
                        locationClient
                            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                            .addOnSuccessListener {
                                if (continuation.isActive) continuation.resume(it)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private data class PendingSearch(
        val query: String,
        val onSelected: (KakaoPlace) -> Unit
    )
}
