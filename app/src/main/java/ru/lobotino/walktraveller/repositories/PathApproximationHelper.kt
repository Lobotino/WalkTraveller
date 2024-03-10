package ru.lobotino.walktraveller.repositories

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import ru.lobotino.walktraveller.model.map.CoordinatePoint
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.utils.ext.toCoordinatePoint

object PathApproximationHelper {

    /**
     * @param pathPoints - all path points to approximate
     * @param approximationDistance - minimum distance between points to delete one of them
     */
    fun approximatePathPoints(
        pathPoints: List<MapPoint>,
        approximationDistance: Float
    ): List<MapPoint> {
        val approximatedPath = ArrayList<MapPoint>()
        if (pathPoints.size < 3) {
            return pathPoints
        }

        val startLinePoint = pathPoints[0].toCoordinatePoint()

        val endLinePoint = pathPoints[pathPoints.size - 1].toCoordinatePoint()

        val lineEquation = buildLineEquation(
            startLinePoint,
            endLinePoint
        )
        var maxDistance = -1.0
        var maxDistancePointIndex = 0

        for (i in 1 until pathPoints.size) {
            val distance = calculateDistanceToLine(pathPoints[i].toCoordinatePoint(), lineEquation)

            if (distance >= maxDistance) {
                maxDistance = distance
                maxDistancePointIndex = i
            }
        }

        if (maxDistance >= approximationDistance) {
            approximatedPath.addAll(
                approximatePathPoints(
                    pathPoints.slice(0 until maxDistancePointIndex + 1),
                    approximationDistance
                )
            )

            approximatedPath.addAll(
                approximatePathPoints(
                    pathPoints.slice(maxDistancePointIndex until pathPoints.size),
                    approximationDistance
                )
            )

            return approximatedPath
        } else {
            return arrayListOf(
                pathPoints[0],
                pathPoints[pathPoints.size - 1]
            )
        }
    }

    private fun buildLineEquation(
        startPathPoint: CoordinatePoint,
        finishPathPoint: CoordinatePoint
    ): LineEquation {
        return if (startPathPoint == finishPathPoint) {
            LineEquation(0.0, 0.0, 0.0)
        } else
            LineEquation(
                finishPathPoint.y - startPathPoint.y,
                -1 * (finishPathPoint.x - startPathPoint.x),
                -1 * startPathPoint.x * (finishPathPoint.y - startPathPoint.y) +
                    startPathPoint.y * (finishPathPoint.x - startPathPoint.x)
            )
    }

    private fun calculateDistanceToLine(
        point: CoordinatePoint,
        lineEquation: LineEquation
    ): Double {
        return abs(
            lineEquation.aParam * point.x +
                lineEquation.bParam * point.y + lineEquation.cParam
        ) / sqrt(
            lineEquation.aParam.pow(2.0) +
                lineEquation.bParam.pow(2.0)
        )
    }

    data class LineEquation(
        val aParam: Double,
        val bParam: Double,
        val cParam: Double
    )
}
