package ru.lobotino.walktraveller.repositories

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

class LocationUpdatesStatesRepository(private val appContext: Context) :
    ILocationUpdatesStatesRepository {

    companion object {
        private const val SHARED_PREFS_TAG = "location_shared_prefs"
        private const val KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates"
    }

    override fun requestingLocationUpdates(): Boolean {
        return appContext.getSharedPreferences(
            SHARED_PREFS_TAG,
            AppCompatActivity.MODE_PRIVATE
        ).getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
    }

    override fun setRequestingLocationUpdates(requestingLocationUpdates: Boolean) {
        appContext.getSharedPreferences(SHARED_PREFS_TAG, AppCompatActivity.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
            .apply()
    }
}