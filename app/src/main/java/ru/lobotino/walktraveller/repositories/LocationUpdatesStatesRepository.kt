package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesStatesRepository

class LocationUpdatesStatesRepository(private val sharedPreferences: SharedPreferences) :
    ILocationUpdatesStatesRepository {

    companion object {
        private const val KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates"
    }

    override fun isRequestingLocationUpdates(): Boolean {
        return sharedPreferences.getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
    }

    override fun setRequestingLocationUpdates(requestingLocationUpdates: Boolean) {
        sharedPreferences
            .edit()
            .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
            .apply()
    }
}