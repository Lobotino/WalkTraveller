package ru.lobotino.walktraveller.repositories

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import ru.lobotino.walktraveller.App.Companion.SHARED_PREFS_TAG
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesStatesRepository

class LocationUpdatesStatesRepository(private val appContext: Context) :
    ILocationUpdatesStatesRepository {

    companion object {
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