package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository

class DefaultLocationRepository(private val sharedPreferences: SharedPreferences) : IDefaultLocationRepository {

    companion object {
        private const val DEFAULT_CITY_LATITUDE = 55.1540200
        private const val DEFAULT_CITY_LONGITUDE = 61.4291500
        private const val DEFAULT_USER_LATITUDE_TAG = "default_user_latitude"
        private const val DEFAULT_USER_LONGITUDE_TAG = "default_user_longitude"
    }

    override fun getDefaultUserLocation(): MapPoint {
        return MapPoint(
            sharedPreferences.getString(DEFAULT_USER_LATITUDE_TAG, null)?.toDoubleOrNull()
                ?: DEFAULT_CITY_LATITUDE,
            sharedPreferences.getString(DEFAULT_USER_LONGITUDE_TAG, null)?.toDoubleOrNull()
                ?: DEFAULT_CITY_LONGITUDE
        )
    }

    override fun setDefaultUserLocation(point: MapPoint) {
        sharedPreferences.edit().apply {
            putString(DEFAULT_USER_LATITUDE_TAG, point.latitude.toString())
            putString(DEFAULT_USER_LONGITUDE_TAG, point.longitude.toString())
            apply()
        }
    }
}