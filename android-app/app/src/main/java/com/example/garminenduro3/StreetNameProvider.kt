package com.example.garminenduro3

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StreetNameProvider(context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder = Geocoder(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _streetName = MutableStateFlow("")
    val streetName: StateFlow<String> = _streetName

    var lastLocation: Location? = null
        private set
    var lastBearing: Float = 0f
        private set

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            if (location.hasBearing()) lastBearing = location.bearing
            val prev = lastLocation
            if (prev == null || location.distanceTo(prev) >= 10f) {
                lastLocation = location
                geocode(location.latitude, location.longitude)
            }
        }
    }

    @Suppress("MissingPermission")
    fun start() {
        if (!Geocoder.isPresent()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, appContext.mainLooper)
    }

    fun stop() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun geocode(lat: Double, lon: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lon, 1) { addresses ->
                _streetName.value = addresses.firstOrNull()?.thoroughfare ?: ""
            }
        } else {
            scope.launch(Dispatchers.IO) {
                val name = try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.thoroughfare ?: ""
                } catch (_: Exception) {
                    ""
                }
                _streetName.value = name
            }
        }
    }
}
