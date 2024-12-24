package ru.lobotino.walktraveller.usecases

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.PathApproximationHelper
import ru.lobotino.walktraveller.repositories.interfaces.ICachePathsRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.utils.ext.toMapPathSegment
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class LocalMapPathsInteractor(
    private val databasePathRepository: IPathRepository,
    private val cachePathRepository: ICachePathsRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val lastCreatedPathIdRepository: ILastCreatedPathIdRepository,
    private val pathRedactor: IPathRedactor,
    private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository,
) : IMapPathsInteractor {

    companion object {
        private val TAG = LocalMapPathsInteractor::class.java.canonicalName
        private val ratingList = SegmentRating.values()
    }

    override suspend fun getAllSavedPathsAsCommon(): List<MapCommonPath> {
        return ArrayList<MapCommonPath>().apply {
            for (path in withContext(defaultDispatcher) { databasePathRepository.getAllPathsInfo() }) {
                val commonPath =
                    getSavedCommonPath(pathId = path.id, isOptimized = true) ?: continue
                add(commonPath)
            }
        }
    }

    override suspend fun getLastSavedRatingPath(): MapRatingPath? {
        val pathId = lastCreatedPathIdRepository.getLastCreatedPathId() ?: return null
        return getSavedRatingPath(pathId = pathId, withRatingOnly = false, isOptimized = false)
    }

    override suspend fun getAllSavedRatingPaths(withRatingOnly: Boolean): List<MapRatingPath> {
        return coroutineScope {
            val resultList = ArrayList<MapRatingPath>()
            for (pathInfo in withContext(defaultDispatcher) { databasePathRepository.getAllPathsInfo() }) {
                val ratingPath = getSavedRatingPath(
                    pathId = pathInfo.id,
                    withRatingOnly = withRatingOnly,
                    isOptimized = true
                )
                if (ratingPath != null) {
                    resultList.add(ratingPath)
                }
            }
            return@coroutineScope resultList
        }
    }

    override suspend fun getAllSavedPathsInfo(): List<MapPathInfo> {
        return coroutineScope {
            val resultList = ArrayList<MapPathInfo>()
            val isWritingPathNow = writingPathStatesRepository.isWritingPathNow()
            val writingPathId = if (isWritingPathNow) {
                lastCreatedPathIdRepository.getLastCreatedPathId()
            } else {
                null
            }
            for (path in withContext(defaultDispatcher) { databasePathRepository.getAllPathsInfo() }) {
                if (isWritingPathNow && path.id == writingPathId) {
                    continue
                }
                var cachedMapPathInfo = cachePathRepository.getMapPathInfo(path.id)
                if (cachedMapPathInfo != null) {
                    if (cachedMapPathInfo.length == 0f) {
                        Log.d(
                            TAG,
                            "Cached path $path has length = 0, calculate it for first time"
                        )
                        val savedCommonPath = getSavedCommonPath(path.id, false)
                        if (savedCommonPath != null) {
                            cachedMapPathInfo = cachedMapPathInfo.copy(
                                length = pathRedactor.updatePathLength(savedCommonPath)
                            )
                            cachePathRepository.savePathInfo(cachedMapPathInfo)
                        }
                    }

                    if (cachedMapPathInfo.mostCommonRating == MostCommonRating.UNKNOWN && cachedMapPathInfo.length > 0f) {
                        Log.d(
                            TAG,
                            "Cached path $path has UNKNOWN mostCommonRating, calculate it for first time"
                        )
                        val savedRatingPath = getSavedRatingPath(
                            path.id,
                            withRatingOnly = false,
                            isOptimized = true
                        )
                        if (savedRatingPath != null) {
                            cachedMapPathInfo = cachedMapPathInfo.copy(
                                mostCommonRating = pathRedactor.updatePathMostCommonRating(
                                    savedRatingPath
                                )
                            )
                            cachePathRepository.savePathInfo(cachedMapPathInfo)
                        }
                    }

                    resultList.add(cachedMapPathInfo)
                    continue
                }

                val pathStartSegment = withContext(defaultDispatcher) {
                    databasePathRepository.getPathStartSegment(path.id)
                } ?: continue

                var pathLength = path.length
                if (pathLength == 0f) {
                    val savedCommonPath = getSavedCommonPath(path.id, false)
                    if (savedCommonPath != null) {
                        pathLength = pathRedactor.updatePathLength(savedCommonPath)
                    }
                }

                var pathMostCommonRating = MostCommonRating.values()[path.mostCommonRating]
                if (pathMostCommonRating == MostCommonRating.UNKNOWN && pathLength > 0f) {
                    Log.d(
                        TAG,
                        "Database path $path has UNKNOWN mostCommonRating, calculate it for first time"
                    )
                    val savedRatingPath =
                        getSavedRatingPath(path.id, withRatingOnly = false, isOptimized = false)
                    if (savedRatingPath != null) {
                        pathMostCommonRating =
                            pathRedactor.updatePathMostCommonRating(savedRatingPath)
                    }
                }

                resultList.add(
                    MapPathInfo(
                        path.id,
                        pathStartSegment.timestamp,
                        pathMostCommonRating,
                        pathLength,
                        path.isOuterPath
                    ).also { pathInfo -> cachePathRepository.savePathInfo(pathInfo) }
                )
            }
            resultList.sortByDescending { pathInfo -> pathInfo.timestamp }
            resultList
        }
    }

    override suspend fun getSavedRatingPath(
        pathId: Long,
        withRatingOnly: Boolean,
        isOptimized: Boolean,
    ): MapRatingPath? {
        return coroutineScope {
            val approximationValue =
                optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() ?: 1f

            cachePathRepository.getRatingPath(pathId, approximationValue)
                ?.let { cachedPath -> return@coroutineScope if (withRatingOnly) cachedPath.toRatingOnlyPath() else cachedPath }

            var pathSegments = withContext(defaultDispatcher) {
                databasePathRepository.getAllPathSegments(pathId)
                    .map { it.toMapPathSegment() }
            }

            if (isOptimized) {
                pathSegments = optimizeRatedPath(pathSegments, approximationValue)
            }

            if (pathSegments.isEmpty()) return@coroutineScope null

            if (withRatingOnly) {
                pathSegments = pathSegments.filter { it.rating != SegmentRating.NONE }
            }

            if (pathSegments.isNotEmpty()) {
                MapRatingPath(
                    pathId,
                    pathSegments
                ).also { ratingPath ->
                    tryCacheRatingPath(ratingPath, approximationValue)
                }
            } else {
                null
            }
        }
    }

    override suspend fun getSavedCommonPath(pathId: Long, isOptimized: Boolean): MapCommonPath? {
        return coroutineScope {
            val approximationValue =
                optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() ?: 1f

            cachePathRepository.getCommonPath(pathId, approximationValue)
                ?.let { return@coroutineScope it }

            var pathPoints = withContext(defaultDispatcher) {
                databasePathRepository.getAllPathPoints(pathId)
                    .map { entityPoint -> entityPoint.toMapPoint() }
            }

            if (isOptimized) {
                pathPoints = optimizeCommonPath(pathPoints, approximationValue)
            }

            if (pathPoints.isEmpty()) return@coroutineScope null

            MapCommonPath(
                pathId,
                pathPoints.first(),
                pathPoints
            ).also { commonPath ->
                cachePathRepository.saveCommonPath(
                    commonPath,
                    approximationValue
                )
            }
        }
    }

    private fun optimizeCommonPath(
        pathPoints: List<MapPoint>,
        approximationValue: Float,
    ): List<MapPoint> {
        return PathApproximationHelper.approximatePathPoints(
            pathPoints,
            approximationValue
        )
    }

    private fun optimizeRatedPath(
        pathSegments: List<MapPathSegment>,
        approximationValue: Float,
    ): List<MapPathSegment> {
        val optimizedResultPaths = ArrayList<MapPathSegment>()
        var currentLine = ArrayList<MapPathSegment>()
        var lastSegmentRating: SegmentRating? = null
        for (segment in pathSegments) {
            if (lastSegmentRating != null && segment.rating != lastSegmentRating) {
                val currentLinePoints = mutableListOf(
                    currentLine[0].startPoint
                ).apply {
                    addAll(currentLine.map { it.finishPoint })
                }
                val optimizedPoints = optimizeCommonPath(currentLinePoints, approximationValue)
                val optimizedSegments = ArrayList<MapPathSegment>()

                if (optimizedPoints.size >= 2) {
                    var lastPoint = optimizedPoints[0]
                    for (index in 1 until optimizedPoints.size) {
                        optimizedSegments.add(
                            MapPathSegment(
                                lastPoint,
                                optimizedPoints[index],
                                lastSegmentRating
                            )
                        )
                        lastPoint = optimizedPoints[index]
                    }
                    optimizedResultPaths.addAll(optimizedSegments)
                }
                currentLine = ArrayList()
            }
            currentLine.add(segment)
            lastSegmentRating = segment.rating
        }
        if (currentLine.isNotEmpty()) {
            optimizedResultPaths.addAll(currentLine)
        }
        return optimizedResultPaths
    }

    private suspend fun EntityPathSegment.toMapPathSegment(): MapPathSegment? {
        return coroutineScope {
            val getStartPoint =
                async(defaultDispatcher) {
                    databasePathRepository.getPointInfo(startPointId)
                }
            val getFinishPoint =
                async(defaultDispatcher) {
                    databasePathRepository.getPointInfo(finishPointId)
                }

            val startPoint = getStartPoint.await()
            val finishPoint = getFinishPoint.await()
            if (startPoint == null || finishPoint == null) return@coroutineScope null

            MapPathSegment(
                startPoint.toMapPoint(),
                finishPoint.toMapPoint(),
                ratingList[rating]
            )
        }
    }

    private fun tryCacheRatingPath(ratingPath: MapRatingPath, approximationValue: Float) {
        if (writingPathStatesRepository.isWritingPathNow()) {
            if (ratingPath.pathId != lastCreatedPathIdRepository.getLastCreatedPathId()) {
                cachePathRepository.saveRatingPath(ratingPath, approximationValue)
            }
        } else {
            cachePathRepository.saveRatingPath(ratingPath, approximationValue)
        }
    }

    private fun MapRatingPath.toRatingOnlyPath(): MapRatingPath {
        return this.copy(pathSegments = this.pathSegments.filter { it.rating != SegmentRating.NONE })
    }
}
