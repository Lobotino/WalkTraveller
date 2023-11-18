package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.PathApproximationHelper
import ru.lobotino.walktraveller.repositories.interfaces.ICachePathsRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class LocalMapPathsInteractor(
    private val databasePathRepository: IPathRepository,
    private val cachePathRepository: ICachePathsRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val lastCreatedPathIdRepository: ILastCreatedPathIdRepository,
    private val pathRedactor: IPathRedactor,
    private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository
) : IMapPathsInteractor {

    companion object {
        private val ratingList = SegmentRating.values()
    }

    override suspend fun getAllSavedPathsAsCommon(): List<MapCommonPath> {
        return coroutineScope {
            ArrayList<MapCommonPath>().apply {
                for (path in withContext(defaultDispatcher) { databasePathRepository.getAllPathsInfo() }) {
                    val cachedPath = cachePathRepository.getCommonPath(path.id)
                    if (cachedPath != null) {
                        add(cachedPath)
                        continue
                    }

                    val getStartPoint =
                        async(defaultDispatcher) { databasePathRepository.getPointInfo(path.startPointId) }

                    val getAllPoints =
                        async(defaultDispatcher) { databasePathRepository.getAllPathPoints(path.id) }

                    val startPoint = getStartPoint.await() ?: continue

                    val pathPoints = getAllPoints.await()
                    if (pathPoints.isEmpty()) continue

                    add(MapCommonPath(
                        path.id,
                        startPoint.toMapPoint(),
                        pathPoints.map { it.toMapPoint() })
                        .also { ratingPath ->
                            cachePathRepository.saveCommonPath(ratingPath)
                        }
                    )
                }
            }
        }
    }

    override suspend fun getLastSavedRatingPath(): MapRatingPath? {
        return coroutineScope {
            mapRatingPath(withContext(defaultDispatcher) { databasePathRepository.getLastPathInfo() })?.also { ratingPath ->
                tryCacheRatingPath(ratingPath)
            }
        }
    }

    override suspend fun getAllSavedRatingPaths(withRatingOnly: Boolean): List<MapRatingPath> {
        return coroutineScope {
            ArrayList<MapRatingPath>().apply {
                for (path in withContext(defaultDispatcher) { databasePathRepository.getAllPathsInfo() }) {
                    val cachedPath = cachePathRepository.getRatingPath(path.id)
                    if (cachedPath != null) {
                        add(if (withRatingOnly) cachedPath.toRatingOnlyPath() else cachedPath)
                        continue
                    }

                    mapRatingPath(path)?.let { ratingPath ->
                        tryCacheRatingPath(ratingPath)
                        add(if (withRatingOnly) ratingPath.toRatingOnlyPath() else ratingPath)
                    }
                }
            }
        }
    }

    private suspend fun mapRatingPath(path: EntityPath?): MapRatingPath? {
        return coroutineScope {
            if (path != null) {
                MapRatingPath(
                    path.id,
                    ArrayList<MapPathSegment>().apply {
                        for (entityPathSegment in withContext(defaultDispatcher) {
                            databasePathRepository.getAllPathSegments(
                                path.id
                            )
                        }) {
                            add(entityPathSegment.toMapPathSegment() ?: continue)
                        }
                    })
            } else {
                null
            }
        }
    }

    override suspend fun getAllSavedPathsInfo(): List<MapPathInfo> {
        return coroutineScope {
            ArrayList<MapPathInfo>().apply {
                for (path in withContext(defaultDispatcher) { databasePathRepository.getAllPathsInfo() }) {
                    var cachedMapPathInfo = cachePathRepository.getMapPathInfo(path.id)
                    if (cachedMapPathInfo != null) {
                        if (cachedMapPathInfo.length == 0f) {
                            val savedCommonPath = getSavedCommonPath(path.id)
                            if (savedCommonPath != null) {
                                cachedMapPathInfo = cachedMapPathInfo.copy(
                                    length = pathRedactor.updatePathLength(savedCommonPath)
                                )
                                cachePathRepository.savePathInfo(cachedMapPathInfo)
                            }
                        }

                        if (cachedMapPathInfo.mostCommonRating == MostCommonRating.UNKNOWN && cachedMapPathInfo.length > 0f) {
                            val savedRatingPath = getSavedRatingPath(path.id, false)
                            if (savedRatingPath != null) {
                                cachedMapPathInfo = cachedMapPathInfo.copy(
                                    mostCommonRating = pathRedactor.updatePathMostCommonRating(
                                        savedRatingPath
                                    )
                                )
                                cachePathRepository.savePathInfo(cachedMapPathInfo)
                            }
                        }

                        add(cachedMapPathInfo)
                        continue
                    }

                    val pathStartSegment = withContext(defaultDispatcher) {
                        databasePathRepository.getPathStartSegment(path.id)
                    } ?: continue

                    var pathLength = path.length
                    if (pathLength == 0f) {
                        val savedCommonPath = getSavedCommonPath(path.id)
                        if (savedCommonPath != null) {
                            pathLength = pathRedactor.updatePathLength(savedCommonPath)
                        }
                    }

                    var pathMostCommonRating = MostCommonRating.values()[path.mostCommonRating]
                    if (pathMostCommonRating == MostCommonRating.UNKNOWN && pathLength > 0f) {
                        val savedRatingPath = getSavedRatingPath(path.id, false)
                        if (savedRatingPath != null) {
                            pathMostCommonRating =
                                pathRedactor.updatePathMostCommonRating(savedRatingPath)
                        }
                    }

                    add(
                        MapPathInfo(
                            path.id,
                            pathStartSegment.timestamp,
                            pathMostCommonRating,
                            pathLength,
                            path.isOuterPath
                        ).also { pathInfo -> cachePathRepository.savePathInfo(pathInfo) }
                    )
                }
                sortByDescending { pathInfo -> pathInfo.timestamp }
            }
        }
    }

    override suspend fun getSavedRatingPath(pathId: Long, withRatingOnly: Boolean): MapRatingPath? {
        return coroutineScope {
            cachePathRepository.getRatingPath(pathId)
                ?.let { cachedPath -> return@coroutineScope if (withRatingOnly) cachedPath.toRatingOnlyPath() else cachedPath }

            val pathSegments = withContext(defaultDispatcher) {
                databasePathRepository.getAllPathSegments(pathId)
            }

            if (pathSegments.isEmpty()) return@coroutineScope null

            val resultPathSegments = ArrayList<MapPathSegment>()
            for (path in pathSegments) {
                if (withRatingOnly && path.rating != SegmentRating.NONE.ordinal || !withRatingOnly) {
                    resultPathSegments.add(path.toMapPathSegment() ?: continue)
                }
            }

            if (resultPathSegments.isNotEmpty()) {
                MapRatingPath(
                    pathId,
                    resultPathSegments
                ).also { ratingPath ->
                    tryCacheRatingPath(ratingPath)
                }
            } else {
                null
            }
        }
    }

    override suspend fun getSavedCommonPath(pathId: Long): MapCommonPath? {
        return coroutineScope {
            cachePathRepository.getCommonPath(pathId)?.let { return@coroutineScope it }

            val pathPoints = withContext(defaultDispatcher) {
                databasePathRepository.getAllPathPoints(pathId)
                    .map { entityPoint -> entityPoint.toMapPoint() }
            }

            if (pathPoints.isEmpty()) return@coroutineScope null

            val optimizedPathPoints = PathApproximationHelper.approximatePathPoints(
                pathPoints,
                optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() ?: 1f
            ) //TODO change approximation logic to mapZoom depends

            MapCommonPath(
                pathId,
                optimizedPathPoints[0],
                optimizedPathPoints
            ).also { commonPath -> cachePathRepository.saveCommonPath(commonPath) }
        }
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

    private fun tryCacheRatingPath(ratingPath: MapRatingPath) {
        if (writingPathStatesRepository.isWritingPathNow()) {
            if (ratingPath.pathId != lastCreatedPathIdRepository.getLastCreatedPathId()) {
                cachePathRepository.saveRatingPath(ratingPath)
            }
        } else {
            cachePathRepository.saveRatingPath(ratingPath)
        }
    }

    private fun MapRatingPath.toRatingOnlyPath(): MapRatingPath {
        return this.copy(pathSegments = this.pathSegments.filter { it.rating != SegmentRating.NONE })
    }
}
