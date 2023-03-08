package ru.lobotino.walktraveller.repositories

import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathLengthRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPointsDistanceRepository

class PathLengthInMetersRepository(private val mapDistanceRepository: IPointsDistanceRepository) :
    IPathLengthRepository {

    override fun calculatePathLength(allPathPoints: List<MapPoint>): Float {
        var resultLength = 0f
        if (allPathPoints.isNotEmpty()) {
            var lastPoint = allPathPoints[0]
            for (i in 1 until allPathPoints.size) {
                resultLength += mapDistanceRepository.getDistanceBetweenPointsInMeters(
                    lastPoint,
                    allPathPoints[i]
                )
                lastPoint = allPathPoints[i]
            }
        }
        return resultLength
    }
}