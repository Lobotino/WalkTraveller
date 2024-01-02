package ru.lobotino.walktraveller.repositories

import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathDistancesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPointsDistanceRepository

class PathDistancesInMetersRepository(private val mapDistanceRepository: IPointsDistanceRepository) :
    IPathDistancesRepository {

    override fun calculatePathLength(allPathPoints: Array<MapPoint>): Float {
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

    override fun calculatePathLength(allPathSegments: Array<MapPathSegment>): Float {
        var resultLength = 0f
        for (segment in allPathSegments) {
            resultLength += mapDistanceRepository.getDistanceBetweenPointsInMeters(
                segment.startPoint,
                segment.finishPoint
            )
        }
        return resultLength
    }

    override fun calculateMostCommonPathRating(allPathSegments: Array<MapPathSegment>): MostCommonRating {
        val allRatingsSegments = arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        for (segment in allPathSegments) {
            allRatingsSegments[segment.rating.ordinal] =
                allRatingsSegments[segment.rating.ordinal] + mapDistanceRepository.getDistanceBetweenPointsInMeters(
                    segment.startPoint,
                    segment.finishPoint
                )
        }

        var longestSegment = 0.0
        var mostCommonRating = MostCommonRating.UNKNOWN
        for (i in 0 until allRatingsSegments.size) {
            if (longestSegment < allRatingsSegments[i]) {
                longestSegment = allRatingsSegments[i]
                mostCommonRating = MostCommonRating.values()[i]
            }
        }
        return mostCommonRating
    }
}
