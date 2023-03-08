package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint

interface IPathDistancesRepository {

    fun calculatePathLength(allPathPoints: List<MapPoint>): Float

    fun calculateMostCommonPathRating(allPathSegments: List<MapPathSegment>): MostCommonRating

}