package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint

interface IPathDistancesRepository {

    fun calculatePathLength(allPathPoints: Array<MapPoint>): Float

    fun calculatePathLength(allPathSegments: Array<MapPathSegment>): Float

    fun calculateMostCommonPathRating(allPathSegments: Array<MapPathSegment>): MostCommonRating

}