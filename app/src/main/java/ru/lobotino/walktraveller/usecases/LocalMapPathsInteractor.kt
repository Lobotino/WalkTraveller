package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.*
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathColorGenerator
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class LocalMapPathsInteractor(
    private val localPathRepository: IPathRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pathColorGenerator: IPathColorGenerator
) : IMapPathsInteractor {

    companion object {
        private val ratingList = SegmentRating.values()
    }

    override suspend fun getAllSavedCommonPaths(): List<MapCommonPath> {
        return coroutineScope {
            ArrayList<MapCommonPath>().apply {
                for (path in withContext(defaultDispatcher) { localPathRepository.getAllPaths() }) {
                    val getStartPoint =
                        async(defaultDispatcher) { localPathRepository.getPointInfo(path.startPointId) }

                    val getAllPoints =
                        async(defaultDispatcher) { localPathRepository.getAllPathPoints(path.id) }

                    val startPoint = getStartPoint.await() ?: continue

                    val pathPoints = getAllPoints.await()
                    if (pathPoints.isEmpty()) continue

                    add(
                        MapCommonPath(
                            path.id,
                            startPoint.toMapPoint(),
                            pathPoints.map { it.toMapPoint() })
                    )
                }
            }
        }
    }

    override suspend fun getLastSavedRatingPath(): MapRatingPath? {
        return coroutineScope {
            mapRatingPath(withContext(defaultDispatcher) { localPathRepository.getLastPathInfo() })
        }
    }

    override suspend fun getAllSavedRatingPaths(): List<MapRatingPath> {
        return coroutineScope {
            ArrayList<MapRatingPath>().apply {
                for (path in withContext(defaultDispatcher) { localPathRepository.getAllPaths() }) {
                    mapRatingPath(path)?.let { ratingPath -> add(ratingPath) }
                }
            }
        }
    }

    private suspend fun mapRatingPath(path: EntityPath?): MapRatingPath? {
        return coroutineScope {
            if (path != null) {

                val pathStartPoint =
                    withContext(defaultDispatcher) { localPathRepository.getPointInfo(path.startPointId) }
                        ?: return@coroutineScope null

                MapRatingPath(
                    path.id,
                    pathStartPoint.toMapPoint(),
                    ArrayList<MapPathSegment>().apply {
                        for (entityPathSegment in withContext(defaultDispatcher) {
                            localPathRepository.getAllPathSegments(
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
                for (path in withContext(defaultDispatcher) { localPathRepository.getAllPaths() }) {
                    val pathStartSegment = withContext(defaultDispatcher) {
                        localPathRepository.getPathStartSegment(path.id)
                    } ?: continue

                    add(
                        MapPathInfo(
                            path.id,
                            pathStartSegment.timestamp,
                            pathColorGenerator.getColorForPath(path.id)
                        )
                    )
                }
                sortByDescending { pathInfo -> pathInfo.timestamp }
            }
        }
    }

    override suspend fun getSavedRatingPath(pathId: Long): MapRatingPath? {
        return coroutineScope {
            val pathSegments = withContext(defaultDispatcher) {
                localPathRepository.getAllPathSegments(pathId)
            }

            if (pathSegments.isEmpty()) return@coroutineScope null

            val resultPathSegments = ArrayList<MapPathSegment>()
            for (path in pathSegments) {
                resultPathSegments.add(path.toMapPathSegment() ?: continue)
            }

            if (resultPathSegments.isNotEmpty()) {
                MapRatingPath(pathId, resultPathSegments[0].startPoint, resultPathSegments)
            } else {
                null
            }
        }
    }

    override suspend fun getSavedCommonPath(pathId: Long): MapCommonPath? {
        return coroutineScope {
            val pathPoints = withContext(defaultDispatcher) {
                localPathRepository.getAllPathPoints(pathId)
                    .map { entityPoint -> entityPoint.toMapPoint() }
            }

            if (pathPoints.isEmpty()) return@coroutineScope null

            MapCommonPath(pathId, pathPoints[0], pathPoints)
        }
    }

    private suspend fun EntityPathSegment.toMapPathSegment(): MapPathSegment? {
        return coroutineScope {
            val getStartPoint =
                async(defaultDispatcher) {
                    localPathRepository.getPointInfo(startPointId)
                }
            val getFinishPoint =
                async(defaultDispatcher) {
                    localPathRepository.getPointInfo(finishPointId)
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
}
