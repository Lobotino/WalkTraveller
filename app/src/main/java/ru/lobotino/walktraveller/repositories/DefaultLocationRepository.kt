package ru.lobotino.walktraveller.repositories

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import ru.lobotino.walktraveller.App.Companion.SHARED_PREFS_TAG
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository

class DefaultLocationRepository(appContext: Context) : IDefaultLocationRepository {

    companion object {
        private const val DEFAULT_CITY_LATITUDE = 55.1540200
        private const val DEFAULT_CITY_LONGITUDE = 61.4291500
        private const val DEFAULT_USER_LATITUDE_TAG = "default_user_latitude"
        private const val DEFAULT_USER_LONGITUDE_TAG = "default_user_longitude"
    }

    private val sharedPreferences = appContext.getSharedPreferences(
        SHARED_PREFS_TAG,
        AppCompatActivity.MODE_PRIVATE
    )

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