package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IMapCameraStateRepository

class MapCameraStateRepository(private val sharedPreferences: SharedPreferences) :
    IMapCameraStateRepository {

    companion object {
        private const val LAST_SEEN_POINT_LATITUDE_TAG = "last_seen_point_latitude"
        private const val LAST_SEEN_POINT_LONGITUDE_TAG = "last_seen_point_longitude"
        private const val LAST_SEEN_ZOOM_TAG = "last_seen_zoom"

        // Migration fallback: legacy installs store only the point.
        private const val DEFAULT_ZOOM = 15.0
    }

    override fun getLastCameraState(): MapCameraState? {
        val latitude =
            sharedPreferences.getString(LAST_SEEN_POINT_LATITUDE_TAG, null)?.toDoubleOrNull()
                ?: return null

        val longitude =
            sharedPreferences.getString(LAST_SEEN_POINT_LONGITUDE_TAG, null)?.toDoubleOrNull()
                ?: return null

        val zoom =
            sharedPreferences.getString(LAST_SEEN_ZOOM_TAG, null)?.toDoubleOrNull()
                ?: DEFAULT_ZOOM

        return MapCameraState(MapPoint(latitude, longitude), zoom)
    }

    override fun setLastCameraState(state: MapCameraState) {
        sharedPreferences.edit().apply {
            putString(LAST_SEEN_POINT_LATITUDE_TAG, state.center.latitude.toString())
            putString(LAST_SEEN_POINT_LONGITUDE_TAG, state.center.longitude.toString())
            putString(LAST_SEEN_ZOOM_TAG, state.zoom.toString())
            apply()
        }
    }
}
