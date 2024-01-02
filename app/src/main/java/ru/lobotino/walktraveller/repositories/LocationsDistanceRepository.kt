package ru.lobotino.walktraveller.repositories

import android.location.Location
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPointsDistanceRepository

class LocationsDistanceRepository : IPointsDistanceRepository {
    override fun getDistanceBetweenPointsInMeters(from: MapPoint, to: MapPoint): Float {
        val result = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, result)
        return result[0]
    }
}
