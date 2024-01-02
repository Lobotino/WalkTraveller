package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.ILastSeenPointRepository

class LastSeenPointRepository(private val sharedPreferences: SharedPreferences) :
    ILastSeenPointRepository {

    companion object {
        private const val LAST_SEEN_POINT_LATITUDE_TAG = "last_seen_point_latitude"
        private const val LAST_SEEN_POINT_LONGITUDE_TAG = "last_seen_point_longitude"
    }

    override fun getLastSeenPoint(): MapPoint? {
        val latitude =
            sharedPreferences.getString(LAST_SEEN_POINT_LATITUDE_TAG, null)?.toDoubleOrNull()
                ?: return null

        val longitude =
            sharedPreferences.getString(LAST_SEEN_POINT_LONGITUDE_TAG, null)?.toDoubleOrNull()
                ?: return null

        return MapPoint(latitude, longitude)
    }

    override fun setLastSeenPoint(point: MapPoint) {
        sharedPreferences.edit().apply {
            putString(LAST_SEEN_POINT_LATITUDE_TAG, point.latitude.toString())
            putString(LAST_SEEN_POINT_LONGITUDE_TAG, point.longitude.toString())
            apply()
        }
    }
}
