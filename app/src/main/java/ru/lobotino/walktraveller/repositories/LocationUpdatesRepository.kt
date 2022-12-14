package ru.lobotino.walktraveller.repositories

import android.location.Location
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository

class LocationUpdatesRepository(
    private val fusedLocationClient: FusedLocationProviderClient
) : ILocationUpdatesRepository {

    companion object {
        private val TAG = LocationUpdatesRepository::class.java.canonicalName
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 5000
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

    private val regularLocationRequest =
        LocationRequest.Builder(UPDATE_INTERVAL_IN_MILLISECONDS).apply {
            setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        }.build()

    private val currentLocationRequest =
        CurrentLocationRequest.Builder().apply {
            setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        }.build()

    init {
        prepareLocationClient()
    }

    private fun prepareLocationClient() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    locationUpdatesFlow.tryEmit(location)
                } else {
                    val errorMessage = "Failed to get location."
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
                regularLocationRequest,
                onNewLocation,
                null
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

    override fun updateLocationNow() {
        try {
            fusedLocationClient.getCurrentLocation(
                currentLocationRequest,
                object : CancellationToken() {
                    override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                        CancellationTokenSource().token

                    override fun isCancellationRequested() = false
                })
                .addOnSuccessListener { location: Location? ->
                    if (location == null)
                        Log.e(TAG, "Cannot update location now.")
                    else {
                        locationUpdatesFlow.tryEmit(location)
                    }
                }
        } catch (unlikely: SecurityException) {
            val errorMessage = "Lost location permission.$unlikely"
            Log.e(TAG, errorMessage)
            locationUpdatesErrorsFlow.tryEmit(errorMessage)
        }
    }

    override fun observeLocationUpdates(): Flow<Location> {
        return locationUpdatesFlow
    }

    override fun observeLocationUpdatesErrors(): Flow<String> {
        return locationUpdatesErrorsFlow
    }
}