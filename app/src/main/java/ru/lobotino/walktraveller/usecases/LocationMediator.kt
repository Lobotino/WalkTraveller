package ru.lobotino.walktraveller.usecases

import android.location.Location
import android.util.Log

class LocationMediator(private var lastLocation: Location? = null) : ILocationMediator {

    companion object {
        private val TAG = LocationMediator::class.java.canonicalName
        private const val MAX_REAL_DISTANCE_BETWEEN_LOCATIONS_IN_METERS = 200 * 1000
    }

    override fun onNewLocation(
        newLocation: Location,
        realLocation: ((Location) -> Unit)?
    ) {
        if (lastLocation == null || lastLocation!!.distanceTo(newLocation) < MAX_REAL_DISTANCE_BETWEEN_LOCATIONS_IN_METERS) {
            lastLocation = newLocation
            realLocation?.invoke(newLocation)
        } else {
            Log.d(
                TAG, "Fake location expected. " +
                        "Last location: ${lastLocation?.latitude}, ${lastLocation?.longitude}. " +
                        "New location: ${newLocation.latitude}, ${newLocation.longitude}"
            )
        }
    }
}