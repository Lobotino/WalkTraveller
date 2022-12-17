package ru.lobotino.walktraveller.repositories

import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class LocationUpdatesRepository(
    private val fusedLocationClient: FusedLocationProviderClient
) : ILocationUpdatesRepository {

    companion object {
        private val TAG = LocationUpdatesRepository::class.java.canonicalName
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
    }

    private val locationUpdatesFlow = MutableSharedFlow<Location>(1, 0, BufferOverflow.DROP_OLDEST)
    private val locationUpdatesErrorsFlow =
        MutableSharedFlow<String>(1, 0, BufferOverflow.DROP_OLDEST)

    private val onNewLocation: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            locationResult.lastLocation?.let { location ->
                locationUpdatesFlow.tryEmit(location)
            }
        }
    }

    private val locationRequest =
        LocationRequest.Builder(UPDATE_INTERVAL_IN_MILLISECONDS).build()

    init {
        prepareLocationClient()
    }

    private fun prepareLocationClient() {
        try {
            fusedLocationClient.lastLocation.addOnCompleteListener { newLocationTask ->
                if (newLocationTask.isSuccessful && newLocationTask.result != null) {
                    locationUpdatesFlow.tryEmit(newLocationTask.result)
                } else {
                    val errorMessage = if (newLocationTask.exception != null) {
                        "Failed to get location.\n${newLocationTask.exception}"
                    } else {
                        "Failed to get location."
                    }
                    Log.w(TAG, errorMessage)
                    locationUpdatesErrorsFlow.tryEmit(errorMessage)
                }
            }
        } catch (unlikely: SecurityException) {
            val errorMessage = "Lost location permission.$unlikely"
            Log.e(TAG, errorMessage)
            locationUpdatesErrorsFlow.tryEmit(errorMessage)
        }
    }

    override fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                onNewLocation, Looper.myLooper()
            )
        } catch (unlikely: SecurityException) {
            val errorMessage = "Lost location permission. Could not request updates. $unlikely"
            Log.e(TAG, errorMessage)
            locationUpdatesErrorsFlow.tryEmit(errorMessage)
        }
    }

    override fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(onNewLocation)
    }

    override fun observeLocationUpdates(): Flow<Location> {
        return locationUpdatesFlow
    }

    override fun observeLocationUpdatesErrors(): Flow<String> {
        return locationUpdatesErrorsFlow
    }
}