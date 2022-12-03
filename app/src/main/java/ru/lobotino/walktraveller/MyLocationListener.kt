package ru.lobotino.walktraveller

import android.location.Location
import android.location.LocationListener

class MyLocationListener : LocationListener {

    companion object {
        private const val TAG = "LocationListener"
    }

    override fun onLocationChanged(loc: Location) {

    }
}